package com.portfolio.rag.chat;

import com.portfolio.rag.chat.dto.ConversationDTO;
import com.portfolio.rag.chat.dto.MessageDTO;
import com.portfolio.rag.common.PageResponse;
import com.portfolio.rag.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /** GET /api/conversations?page=0&size=20 — paginated, newest updated first. */
    @GetMapping
    public PageResponse<ConversationDTO> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return conversationService.list(SecurityUtils.getCurrentUserId(), page, Math.min(size, 50));
    }

    /** GET /api/conversations/{id}/messages — full message history in chronological order. */
    @GetMapping("/{id}/messages")
    public List<MessageDTO> messages(@PathVariable Long id) {
        return conversationService.messages(SecurityUtils.getCurrentUserId(), id);
    }
}
