package com.portfolio.rag.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank @Size(max = 2000, message = "问题长度不能超过 2000 个字符") String question,
        Long conversationId
) {
}
