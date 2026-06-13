package com.portfolio.rag.document;

import com.portfolio.rag.common.ApiException;
import com.portfolio.rag.common.EntityNotFoundException;
import com.portfolio.rag.common.PageResponse;
import com.portfolio.rag.document.dto.DocumentDTO;
import com.portfolio.rag.document.dto.UploadResponse;
import com.portfolio.rag.document.entity.Document;
import com.portfolio.rag.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final byte[] PDF_BYTES = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34}; // "%PDF-1.4"

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentIngestionService ingestionService;

    @InjectMocks
    private DocumentService documentService;

    // ------------------------------------------------------------------
    // upload
    // ------------------------------------------------------------------

    @Test
    void upload_validPdfFile_returnsPendingStatus() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", PDF_BYTES);
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document doc = inv.getArgument(0);
            doc.setId(42L);
            return doc;
        });

        UploadResponse response = documentService.upload(5L, file);

        assertThat(response.documentId()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo(Document.STATUS_PENDING);
        verify(ingestionService).ingest(eq(42L), eq(5L), any(Path.class), eq("application/pdf"));
    }

    @Test
    void upload_validTextFile_detectsTextPlain() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello world".getBytes(StandardCharsets.UTF_8));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document doc = inv.getArgument(0);
            doc.setId(7L);
            return doc;
        });

        UploadResponse response = documentService.upload(5L, file);

        assertThat(response.status()).isEqualTo(Document.STATUS_PENDING);
        verify(ingestionService).ingest(eq(7L), eq(5L), any(Path.class), eq("text/plain"));
    }

    @Test
    void upload_tooLargeFile_throws413() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(20L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> documentService.upload(5L, file))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(ex.getCode()).isEqualTo("PAYLOAD_TOO_LARGE");
                });
        verify(documentRepository, never()).save(any());
    }

    @Test
    void upload_unsupportedType_throws415() {
        // contains NUL bytes -> neither PDF nor text
        byte[] binary = {0x00, 0x01, 0x02, (byte) 0x89, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", binary);

        assertThatThrownBy(() -> documentService.upload(5L, file))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(ex.getCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
                });
        verify(documentRepository, never()).save(any());
    }

    @Test
    void upload_emptyFile_throws400() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> documentService.upload(5L, file))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getCode()).isEqualTo("VALIDATION_FAILED");
                });
    }

    // ------------------------------------------------------------------
    // list
    // ------------------------------------------------------------------

    @Test
    void list_returnsUserScopedPage() {
        Document doc = Document.builder()
                .id(1L).userId(5L).filename("a.pdf").fileSize(100L)
                .mimeType("application/pdf").status(Document.STATUS_DONE)
                .chunkCount(3).createdAt(Instant.now())
                .build();
        when(documentRepository.findByUserId(eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doc), PageRequest.of(0, 10), 1));

        PageResponse<DocumentDTO> page = documentService.list(5L, 0, 10);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).filename()).isEqualTo("a.pdf");
        assertThat(page.content().get(0).status()).isEqualTo("done");
        verify(documentRepository).findByUserId(eq(5L), any(Pageable.class));
    }

    // ------------------------------------------------------------------
    // delete
    // ------------------------------------------------------------------

    @Test
    void delete_ownDocument_succeeds() {
        Document doc = Document.builder().id(9L).userId(5L).filename("a.pdf").fileSize(1L).build();
        when(documentRepository.findByIdAndUserId(9L, 5L)).thenReturn(Optional.of(doc));

        documentService.delete(5L, 9L);

        verify(documentRepository).delete(doc);
    }

    @Test
    void delete_otherUserDocument_throws404() {
        // ownership scoping: lookup by (id, userId) finds nothing for non-owners
        when(documentRepository.findByIdAndUserId(9L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.delete(5L, 9L))
                .isInstanceOf(EntityNotFoundException.class);
        verify(documentRepository, never()).delete(any(Document.class));
    }

    @Test
    void delete_nonExistentDocument_throws404() {
        when(documentRepository.findByIdAndUserId(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.delete(5L, 12345L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
