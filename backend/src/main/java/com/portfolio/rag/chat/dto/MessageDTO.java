package com.portfolio.rag.chat.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.portfolio.rag.chat.entity.Message;

import java.time.Instant;

public record MessageDTO(
        Long id,
        Long conversationId,
        String role,
        String content,
        @JsonRawValue String sources,
        Instant createdAt
) {

    public static MessageDTO from(Message message) {
        return new MessageDTO(
                message.getId(),
                message.getConversationId(),
                message.getRole(),
                message.getContent(),
                message.getSources(),
                message.getCreatedAt()
        );
    }
}
