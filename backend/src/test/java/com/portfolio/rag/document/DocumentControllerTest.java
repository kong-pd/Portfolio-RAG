package com.portfolio.rag.document;

import com.portfolio.rag.auth.JwtService;
import com.portfolio.rag.common.PageResponse;
import com.portfolio.rag.config.JwtAuthenticationFilter;
import com.portfolio.rag.config.SecurityConfig;
import com.portfolio.rag.document.dto.DocumentDTO;
import com.portfolio.rag.document.dto.UploadResponse;
import com.portfolio.rag.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class DocumentControllerTest {

    private static final String VALID_TOKEN = "valid-token";
    private static final long USER_ID = 7L;
    private static final byte[] PDF_BYTES = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;
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
    void upload_authenticated_returns201() throws Exception {
        given(documentService.upload(eq(USER_ID), any()))
                .willReturn(new UploadResponse(42L, "pending"));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "test.pdf", "application/pdf", PDF_BYTES))
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(42))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void upload_unauthenticated_returns401() throws Exception {
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "test.pdf", "application/pdf", PDF_BYTES)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(documentService, never()).upload(any(), any());
    }

    @Test
    void listDocuments_authenticated_returns200() throws Exception {
        DocumentDTO dto = new DocumentDTO(1L, "a.pdf", 100L, "application/pdf",
                "done", 3, null, Instant.parse("2026-06-13T00:00:00Z"));
        given(documentService.list(USER_ID, 0, 10))
                .willReturn(new PageResponse<>(List.of(dto), 0, 10, 1, 1));

        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].filename").value("a.pdf"))
                .andExpect(jsonPath("$.content[0].status").value("done"));
    }

    @Test
    void deleteDocument_authenticated_returns204() throws Exception {
        mockMvc.perform(delete("/api/documents/5")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());

        verify(documentService).delete(USER_ID, 5L);
    }

    @Test
    void deleteDocument_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/documents/5"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(documentService, never()).delete(any(), any());
    }
}
