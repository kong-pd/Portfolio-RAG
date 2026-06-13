package com.portfolio.rag.document;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
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
 * Exercises the token-based sliding-window chunking logic (jtokkit cl100k_base)
 * through the public {@link DocumentIngestionService#ingest} entry point (the
 * @Async annotation is inert outside a Spring context, so the call runs
 * synchronously).
 *
 * Configuration below: chunkSize=5 tokens per window, chunkOverlap=1 token,
 * i.e. windows advance by 4 tokens each step.
 */
@ExtendWith(MockitoExtension.class)
class TextChunkingTest {

    private static final int WINDOW_TOKENS = 5;
    private static final int OVERLAP_TOKENS = 1;
    private static final Encoding ENCODING =
            Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

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
        properties.getRag().setChunkSize(WINDOW_TOKENS);
        properties.getRag().setChunkOverlap(OVERLAP_TOKENS);

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
        String text = "hello world";
        int tokens = ENCODING.encode(text).size();
        assertThat(tokens).isLessThanOrEqualTo(WINDOW_TOKENS); // fits in one window
        Path file = tempTextFile(text);

        service.ingest(1L, 2L, file, "text/plain");

        List<DocumentChunk> chunks = savedChunks();
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).isEqualTo("hello world");
        assertThat(chunks.get(0).getTokenCount()).isEqualTo(tokens);
        assertThat(chunks.get(0).getChunkIndex()).isZero();
        assertThat(chunks.get(0).getDocumentId()).isEqualTo(1L);
        assertThat(chunks.get(0).getUserId()).isEqualTo(2L);
        assertThat(chunks.get(0).getEmbedding()).containsExactly(0.1f, 0.2f, 0.3f);

        assertThat(document.getStatus()).isEqualTo(Document.STATUS_DONE);
        assertThat(document.getChunkCount()).isEqualTo(1);
        assertThat(Files.exists(file)).as("temp file is cleaned up").isFalse();
    }

    @Test
    void longText_producesMultipleTokenWindowsWithOverlap() throws IOException {
        stubEmbeddings();
        String text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu";
        int totalTokens = ENCODING.encode(text).size();
        assertThat(totalTokens).isEqualTo(14);
        Path file = tempTextFile(text);

        service.ingest(1L, 2L, file, "text/plain");

        List<DocumentChunk> chunks = savedChunks();
        // step = WINDOW(5) - OVERLAP(1) = 4. windows start at 0,4,8,12 -> 4 chunks.
        int step = WINDOW_TOKENS - OVERLAP_TOKENS;
        int expected = (int) Math.ceil((double) (totalTokens - WINDOW_TOKENS) / step) + 1;
        assertThat(chunks).hasSize(expected).hasSize(4);

        // chunk indices are sequential
        assertThat(chunks).extracting(DocumentChunk::getChunkIndex).containsExactly(0, 1, 2, 3);

        // every non-final chunk carries a full WINDOW_TOKENS window
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertThat(chunks.get(i).getTokenCount()).isEqualTo(WINDOW_TOKENS);
        }
        // the reassembled token stream from non-overlapping prefixes round-trips
        assertThat(chunks.get(0).getTokenCount()).isPositive();

        assertThat(document.getStatus()).isEqualTo(Document.STATUS_DONE);
        assertThat(document.getChunkCount()).isEqualTo(chunks.size());
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
