package com.portfolio.rag.chat;

import com.portfolio.rag.chat.dto.ConversationDTO;
import com.portfolio.rag.chat.dto.MessageDTO;
import com.portfolio.rag.common.EntityNotFoundException;
import com.portfolio.rag.common.PageResponse;
import com.portfolio.rag.repository.ConversationRepository;
import com.portfolio.rag.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public PageResponse<ConversationDTO> list(Long userId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return PageResponse.from(
                conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable),
                ConversationDTO::from);
    }

    @Transactional(readOnly = true)
    public List<MessageDTO> messages(Long userId, Long conversationId) {
        // Verify ownership: a missing conversation or one owned by another user is a 404.
        conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new EntityNotFoundException("会话不存在"));

        return messageRepository
                .findByConversationIdAndUserIdOrderByCreatedAtAsc(conversationId, userId)
                .stream()
                .map(MessageDTO::from)
                .toList();
    }
}
