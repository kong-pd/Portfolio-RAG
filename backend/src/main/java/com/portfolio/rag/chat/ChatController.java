package com.portfolio.rag.chat;

import com.portfolio.rag.chat.dto.ChatRequest;
import com.portfolio.rag.common.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagService ragService;

    /**
     * POST /api/chat/stream — returns a text/event-stream SSE response.
     * Events: {@code token} (LLM text fragment), {@code done} (final metadata JSON),
     * {@code error} (error message string).
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3-minute hard timeout
        try {
            ragService.streamAnswer(
                    SecurityUtils.getCurrentUserId(),
                    request.question(),
                    request.conversationId(),
                    emitter);
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
            } catch (IOException ex) { /* ignore */ }
            emitter.complete();
        }
        return emitter;
    }
}
