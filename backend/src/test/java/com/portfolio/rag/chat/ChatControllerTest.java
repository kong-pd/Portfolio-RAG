package com.portfolio.rag.chat;

import com.portfolio.rag.auth.JwtService;
import com.portfolio.rag.chat.dto.ChatResponse;
import com.portfolio.rag.chat.dto.SourceItem;
import com.portfolio.rag.config.JwtAuthenticationFilter;
import com.portfolio.rag.config.SecurityConfig;
import com.portfolio.rag.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ChatControllerTest {

    private static final String VALID_TOKEN = "valid-token";
    private static final long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagService ragService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private UserRepository userRepository; // required by SecurityConfig#userDetailsService

    @BeforeEach
    void stubJwt() {
        given(jwtService.validateDetailed(anyString()))
                .willReturn(new JwtService.ValidationResult(null, JwtService.ERROR_UNAUTHORIZED));
        given(jwtService.validateDetailed(VALID_TOKEN))
                .willReturn(new JwtService.ValidationResult(USER_ID, null));
    }

    @Test
    void askQuestion_authenticated_returns200() throws Exception {
        List<SourceItem> sources = List.of(new SourceItem(10L, 4L, "a.pdf", 0.91, 2));
        given(ragService.ask(eq(USER_ID), eq("What is RAG?"), isNull()))
                .willReturn(new ChatResponse(11L, "RAG is retrieval-augmented generation.", sources));

        mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is RAG?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(11))
                .andExpect(jsonPath("$.content").value("RAG is retrieval-augmented generation."))
                .andExpect(jsonPath("$.sources[0].filename").value("a.pdf"));
    }

    @Test
    void askQuestion_withConversationId_passesItThrough() throws Exception {
        given(ragService.ask(USER_ID, "follow-up", 33L))
                .willReturn(new ChatResponse(33L, "answer", List.of()));

        mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"follow-up\",\"conversationId\":33}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(33));
    }

    @Test
    void askQuestion_emptyQuestion_returns400() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(ragService, never()).ask(any(), any(), any());
    }

    @Test
    void askQuestion_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is RAG?\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(ragService, never()).ask(any(), any(), any());
    }
}
