package com.portfolio.rag.chat.dto;

public record SourceItem(
        Long chunkId,
        Long documentId,
        String filename,
        double score,
        Integer pageNum
) {
}
