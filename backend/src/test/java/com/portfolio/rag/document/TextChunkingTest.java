package com.portfolio.rag.document;

import com.portfolio.rag.config.AppProperties;
import com.portfolio.rag.document.entity.Document;
import com.portfolio.rag.document.entity.DocumentChunk;
import com.portfolio.rag.repository.DocumentChunkRepository;
import com.portfolio.rag.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the sliding-window chunking logic through the public
 * {@link DocumentIngestionService#ingest} entry point (the @Async annotation
 * is inert outside a Spring context, so the call runs synchronously).
 *
 * Configuration below: chunkSize=5 tokens * 4 chars/token = 20-char windows,
 * chunkOverlap=1 token * 4 chars/token = 4-char overlap.
 */
@ExtendWith(MockitoExtension.class)
class TextChunkingTest {

    private static final int CHUNK_CHARS = 20;
    private static final int OVERLAP_CHARS = 4;

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentChunkRepository chunkRepository;
    @Mock
    private EmbeddingModel embeddingModel;

    private DocumentIngestionService service;
    private Document document;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getRag().setChunkSize(CHUNK_CHARS / 4);
        properties.getRag().setChunkOverlap(OVERLAP_CHARS / 4);

        document = Document.builder()
                .id(1L).userId(2L).filename("test.txt").fileSize(1L)
                .status(Document.STATUS_PENDING)
                .build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        service = new DocumentIngestionService(
                documentRepository, chunkRepository, embeddingModel, properties);
    }

    private void stubEmbeddings() {
        when(embeddingModel.embedForResponse(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            List<Embedding> embeddings = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                embeddings.add(new Embedding(new float[]{0.1f, 0.2f, 0.3f}, i));
            }
            return new EmbeddingResponse(embeddings);
        });
    }

    private Path tempTextFile(String content) throws IOException {
        Path file = Files.createTempFile("chunking-test-", ".txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @SuppressWarnings("unchecked")
    private List<DocumentChunk> savedChunks() {
        ArgumentCaptor<List<DocumentChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void shortText_producesSingleChunk() throws IOException {
        stubEmbeddings();
        Path file = tempTextFile("hello world"); // 11 chars < 20-char window

        service.ingest(1L, 2L, file, "text/plain");

        List<DocumentChunk> chunks = savedChunks();
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).isEqualTo("hello world");
        assertThat(chunks.get(0).getChunkIndex()).isZero();
        assertThat(chunks.get(0).getDocumentId()).isEqualTo(1L);
        assertThat(chunks.get(0).getUserId()).isEqualTo(2L);
        assertThat(chunks.get(0).getEmbedding()).containsExactly(0.1f, 0.2f, 0.3f);

        assertThat(document.getStatus()).isEqualTo(Document.STATUS_DONE);
        assertThat(document.getChunkCount()).isEqualTo(1);
        assertThat(Files.exists(file)).as("temp file is cleaned up").isFalse();
    }

    @Test
    void longText_producesMultipleChunksWithOverlap() throws IOException {
        stubEmbeddings();
        // 50 distinct, whitespace-free chars so strip() leaves windows intact:
        // windows [0,20) [16,36) [32,50)
        String text = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMN";
        assertThat(text).hasSize(50);
        Path file = tempTextFile(text);

        service.ingest(1L, 2L, file, "text/plain");

        List<DocumentChunk> chunks = savedChunks();
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).getContent()).isEqualTo(text.substring(0, 20));
        assertThat(chunks.get(1).getContent()).isEqualTo(text.substring(16, 36));
        assertThat(chunks.get(2).getContent()).isEqualTo(text.substring(32, 50));

        // each chunk starts with the last OVERLAP_CHARS of the previous one
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1).getContent();
            String overlap = previous.substring(previous.length() - OVERLAP_CHARS);
            assertThat(chunks.get(i).getContent()).startsWith(overlap);
        }

        assertThat(chunks).extracting(DocumentChunk::getChunkIndex).containsExactly(0, 1, 2);
        assertThat(document.getStatus()).isEqualTo(Document.STATUS_DONE);
        assertThat(document.getChunkCount()).isEqualTo(3);
    }

    @Test
    void emptyText_producesNoChunks_marksDocumentAsError() throws IOException {
        Path file = tempTextFile("");

        service.ingest(1L, 2L, file, "text/plain");

        verify(chunkRepository, never()).saveAll(any());
        assertThat(document.getStatus()).isEqualTo(Document.STATUS_ERROR);
        assertThat(document.getErrorMsg()).contains("文件内容为空");
    }

    @Test
    void blankText_producesNoChunks_marksDocumentAsError() throws IOException {
        Path file = tempTextFile("   \n\t  \n");

        service.ingest(1L, 2L, file, "text/plain");

        verify(chunkRepository, never()).saveAll(any());
        assertThat(document.getStatus()).isEqualTo(Document.STATUS_ERROR);
    }
}
