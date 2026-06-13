package com.portfolio.rag.chat.dto;

import java.util.List;

public record ChatResponse(
        Long conversationId,
        String answer,
        List<SourceItem> sources
) {
}
