package com.portfolio.rag.repository;

import com.portfolio.rag.chat.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Most recent messages first; callers reverse for chronological order. */
    List<Message> findTop6ByConversationIdAndUserIdOrderByCreatedAtDesc(Long conversationId, Long userId);

    /** Full message history in chronological order. */
    List<Message> findByConversationIdAndUserIdOrderByCreatedAtAsc(Long conversationId, Long userId);
}
