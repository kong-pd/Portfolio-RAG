package com.portfolio.rag.chat;

import com.portfolio.rag.auth.JwtService;
import com.portfolio.rag.chat.dto.ConversationDTO;
import com.portfolio.rag.chat.dto.MessageDTO;
import com.portfolio.rag.common.EntityNotFoundException;
import com.portfolio.rag.common.PageResponse;
import com.portfolio.rag.config.JwtAuthenticationFilter;
import com.portfolio.rag.config.SecurityConfig;
import com.portfolio.rag.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConversationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ConversationControllerTest {

    private static final String VALID_TOKEN = "valid-token";
    private static final long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConversationService conversationService;
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
    void list_authenticated_returns200() throws Exception {
        Instant now = Instant.parse("2026-06-13T10:00:00Z");
        var dto = new ConversationDTO(1L, "First chat", now, now);
        given(conversationService.list(eq(USER_ID), anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(dto), 0, 20, 1, 1));

        mockMvc.perform(get("/api/conversations")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("First chat"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(conversationService, never()).list(anyLong(), anyInt(), anyInt());
    }

    @Test
    void messages_authenticated_returns200() throws Exception {
        Instant now = Instant.parse("2026-06-13T10:00:00Z");
        var msg = new MessageDTO(5L, 1L, "user", "Hello", null, now, 3, null);
        given(conversationService.messages(USER_ID, 1L)).willReturn(List.of(msg));

        mockMvc.perform(get("/api/conversations/1/messages")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("Hello"))
                .andExpect(jsonPath("$[0].promptTokens").value(3));
    }

    @Test
    void messages_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/conversations/1/messages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(conversationService, never()).messages(anyLong(), anyLong());
    }

    @Test
    void messages_otherUserConversation_returns404() throws Exception {
        given(conversationService.messages(USER_ID, 99L))
                .willThrow(new EntityNotFoundException("会话不存在"));

        mockMvc.perform(get("/api/conversations/99/messages")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
