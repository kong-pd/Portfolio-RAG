package com.portfolio.rag.chat.dto;

import com.portfolio.rag.chat.entity.Conversation;

import java.time.Instant;

public record ConversationDTO(
        Long id,
        String title,
        Instant createdAt,
        Instant updatedAt
) {

    public static ConversationDTO from(Conversation conversation) {
        return new ConversationDTO(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }
}
