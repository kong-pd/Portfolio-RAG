package com.portfolio.rag.chat.dto;

import java.util.List;

public record ChatResponse(
        Long conversationId,
        String content,
        List<SourceItem> sources
) {
}
