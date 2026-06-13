package com.portfolio.rag.document;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import com.portfolio.rag.config.AppProperties;
import com.portfolio.rag.document.entity.Document;
import com.portfolio.rag.document.entity.DocumentChunk;
import com.portfolio.rag.repository.DocumentChunkRepository;
import com.portfolio.rag.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Background pipeline: parse -> chunk -> embed -> persist.
 * Chunking uses real token boundaries via jtokkit (cl100k_base encoding),
 * with chunk size/overlap configured in tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private static final int EMBEDDING_BATCH_SIZE = 32;

    private static final Encoding ENCODING =
            Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingModel embeddingModel;
    private final AppProperties properties;

    @Async
    public void ingest(Long documentId, Long userId, Path tempFile, String mimeType) {
        try {
            updateStatus(documentId, Document.STATUS_PROCESSING, null, null);

            List<ChunkData> chunks;
            try {
                chunks = parseAndChunk(tempFile, mimeType);
            } catch (Exception e) {
                log.warn("Failed to parse document {}", documentId, e);
                updateStatus(documentId, Document.STATUS_ERROR, null,
                        "无法解析文件：" + rootMessage(e));
                return;
            }

            if (chunks.isEmpty()) {
                updateStatus(documentId, Document.STATUS_ERROR, null, "无法解析文件：文件内容为空");
                return;
            }

            int chunkIndex = 0;
            for (int from = 0; from < chunks.size(); from += EMBEDDING_BATCH_SIZE) {
                List<ChunkData> batch = chunks.subList(from, Math.min(from + EMBEDDING_BATCH_SIZE, chunks.size()));
                EmbeddingResponse response =
                        embeddingModel.embedForResponse(batch.stream().map(ChunkData::text).toList());

                List<DocumentChunk> entities = new ArrayList<>(batch.size());
                for (int i = 0; i < batch.size(); i++) {
                    float[] vector = response.getResults().get(i).getOutput();
                    entities.add(DocumentChunk.builder()
                            .documentId(documentId)
                            .userId(userId)
                            .content(batch.get(i).text())
                            .embedding(vector)
                            .chunkIndex(chunkIndex++)
                            .pageNum(batch.get(i).pageNum())
                            .tokenCount(batch.get(i).tokenCount())
                            .build());
                }
                chunkRepository.saveAll(entities);
            }

            updateStatus(documentId, Document.STATUS_DONE, chunks.size(), null);
            log.info("Document {} ingested: {} chunks", documentId, chunks.size());
        } catch (Exception e) {
            log.error("Ingestion failed for document {}", documentId, e);
            updateStatus(documentId, Document.STATUS_ERROR, null, rootMessage(e));
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("Could not delete temp file {}", tempFile, e);
            }
        }
    }

    /**
     * Re-embeds the chunks already persisted for a document. Used by the retry
     * endpoint when an earlier ingestion failed after chunking (the original
     * upload is not retained, so we cannot re-parse from source).
     */
    @Async
    public void reingest(Long documentId, Long userId) {
        try {
            updateStatus(documentId, Document.STATUS_PROCESSING, null, null);

            List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(documentId);
            if (chunks.isEmpty()) {
                updateStatus(documentId, Document.STATUS_ERROR, null,
                        "无法重试：未找到已解析的文档分块，请重新上传");
                return;
            }

            for (int from = 0; from < chunks.size(); from += EMBEDDING_BATCH_SIZE) {
                List<DocumentChunk> batch = chunks.subList(from, Math.min(from + EMBEDDING_BATCH_SIZE, chunks.size()));
                EmbeddingResponse response =
                        embeddingModel.embedForResponse(batch.stream().map(DocumentChunk::getContent).toList());
                for (int i = 0; i < batch.size(); i++) {
                    batch.get(i).setEmbedding(response.getResults().get(i).getOutput());
                }
                chunkRepository.saveAll(batch);
            }

            updateStatus(documentId, Document.STATUS_DONE, chunks.size(), null);
            log.info("Document {} re-ingested: {} chunks", documentId, chunks.size());
        } catch (Exception e) {
            log.error("Re-ingestion failed for document {}", documentId, e);
            updateStatus(documentId, Document.STATUS_ERROR, null, rootMessage(e));
        }
    }

    private List<ChunkData> parseAndChunk(Path file, String mimeType) throws IOException {
        int windowTokens = properties.getRag().getChunkSize();
        int overlapTokens = properties.getRag().getChunkOverlap();

        List<ChunkData> chunks = new ArrayList<>();
        if ("application/pdf".equals(mimeType)) {
            try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String pageText = stripper.getText(pdf);
                    slidingWindow(pageText, page, windowTokens, overlapTokens, chunks);
                }
            }
        } else {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            slidingWindow(text, null, windowTokens, overlapTokens, chunks);
        }
        return chunks;
    }

    /**
     * Token-level sliding window over cl100k_base tokens: encode the segment once,
     * then emit overlapping windows of {@code windowTokens} tokens advancing by
     * {@code windowTokens - overlapTokens} each step. Each window is decoded back
     * to text and its exact token count recorded.
     */
    private void slidingWindow(String text, Integer pageNum, int windowTokens, int overlapTokens,
                               List<ChunkData> out) {
        if (text == null || text.isBlank()) {
            return;
        }
        IntArrayList tokens = ENCODING.encode(text);
        int total = tokens.size();
        if (total == 0) {
            return;
        }
        int step = Math.max(windowTokens - overlapTokens, 1);
        int start = 0;
        while (start < total) {
            int end = Math.min(start + windowTokens, total);
            IntArrayList window = new IntArrayList(end - start);
            for (int i = start; i < end; i++) {
                window.add(tokens.get(i));
            }
            String piece = ENCODING.decode(window).strip();
            if (!piece.isEmpty()) {
                out.add(new ChunkData(piece, pageNum, end - start));
            }
            if (end == total) {
                break;
            }
            start += step;
        }
    }

    private void updateStatus(Long documentId, String status, Integer chunkCount, String errorMsg) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus(status);
            if (chunkCount != null) {
                doc.setChunkCount(chunkCount);
            }
            doc.setErrorMsg(errorMsg);
            documentRepository.save(doc);
        });
    }

    private String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        return msg != null ? msg : cause.getClass().getSimpleName();
    }

    private record ChunkData(String text, Integer pageNum, Integer tokenCount) {
    }
}
