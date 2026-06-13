package com.portfolio.rag.document.dto;

import com.portfolio.rag.document.entity.Document;

import java.time.Instant;

public record DocumentDTO(
        Long id,
        String filename,
        Long fileSize,
        String mimeType,
        String status,
        int chunkCount,
        String errorMsg,
        Instant createdAt
) {

    public static DocumentDTO from(Document doc) {
        return new DocumentDTO(
                doc.getId(),
                doc.getFilename(),
                doc.getFileSize(),
                doc.getMimeType(),
                doc.getStatus(),
                doc.getChunkCount(),
                doc.getErrorMsg(),
                doc.getCreatedAt()
        );
    }
}
