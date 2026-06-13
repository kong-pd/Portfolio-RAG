package com.portfolio.rag.document;

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
 * Chunk size/overlap are configured in tokens and approximated as 4 chars/token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private static final int CHARS_PER_TOKEN = 4;
    private static final int EMBEDDING_BATCH_SIZE = 32;

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

    private List<ChunkData> parseAndChunk(Path file, String mimeType) throws IOException {
        int chunkChars = properties.getRag().getChunkSize() * CHARS_PER_TOKEN;
        int overlapChars = properties.getRag().getChunkOverlap() * CHARS_PER_TOKEN;

        List<ChunkData> chunks = new ArrayList<>();
        if ("application/pdf".equals(mimeType)) {
            try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String pageText = stripper.getText(pdf);
                    slidingWindow(pageText, page, chunkChars, overlapChars, chunks);
                }
            }
        } else {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            slidingWindow(text, null, chunkChars, overlapChars, chunks);
        }
        return chunks;
    }

    private void slidingWindow(String text, Integer pageNum, int chunkChars, int overlapChars,
                               List<ChunkData> out) {
        if (text == null || text.isBlank()) {
            return;
        }
        int length = text.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + chunkChars, length);
            String piece = text.substring(start, end).strip();
            if (!piece.isEmpty()) {
                out.add(new ChunkData(piece, pageNum));
            }
            if (end == length) {
                break;
            }
            start = end - overlapChars;
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

    private record ChunkData(String text, Integer pageNum) {
    }
}
