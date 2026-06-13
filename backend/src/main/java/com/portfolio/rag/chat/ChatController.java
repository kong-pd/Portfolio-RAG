package com.portfolio.rag.chat;

import com.portfolio.rag.chat.dto.ChatRequest;
import com.portfolio.rag.chat.dto.ChatResponse;
import com.portfolio.rag.common.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagService ragService;

    /**
     * Path kept as /stream per the API contract; the D1-D2 slice returns the
     * full answer as application/json (SSE streaming lands in a later slice).
     */
    @PostMapping(value = "/stream", produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return ragService.ask(SecurityUtils.getCurrentUserId(), request.question(), request.conversationId());
    }
}
