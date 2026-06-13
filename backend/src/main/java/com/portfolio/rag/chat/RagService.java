package com.portfolio.rag.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.rag.chat.dto.ChatResponse;
import com.portfolio.rag.chat.dto.SourceItem;
import com.portfolio.rag.chat.entity.Conversation;
import com.portfolio.rag.chat.entity.Message;
import com.portfolio.rag.common.EntityNotFoundException;
import com.portfolio.rag.config.AppProperties;
import com.portfolio.rag.repository.ConversationRepository;
import com.portfolio.rag.repository.DocumentChunkRepository;
import com.portfolio.rag.repository.DocumentChunkRepository.ChunkSearchResult;
import com.portfolio.rag.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagService {

    private static final String NO_CONTEXT_ANSWER =
            "知识库中未找到与该问题相关的内容。可以尝试换一种问法，或先上传相关文档。";

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一个文档问答助手。请根据以下检索到的文档内容回答用户的问题。回答要准确、简洁，并仅基于提供的内容。如果内容不足以回答问题，请说明。

            [文档内容]
            %s

            [问题]
            %s""";

    private static final int HISTORY_LIMIT = 6;
    private static final int TITLE_MAX_LENGTH = 50;

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final DocumentChunkRepository chunkRepository;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public RagService(ChatClient.Builder chatClientBuilder,
                      EmbeddingModel embeddingModel,
                      ConversationRepository conversationRepository,
                      MessageRepository messageRepository,
                      DocumentChunkRepository chunkRepository,
                      AppProperties properties,
                      ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.chunkRepository = chunkRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatResponse ask(Long userId, String question, Long conversationId) {
        Conversation conversation = resolveConversation(userId, question, conversationId);

        // History must be captured before persisting the new user message.
        List<org.springframework.ai.chat.messages.Message> history =
                loadHistory(conversation.getId(), userId);

        // 1. Embed question and retrieve relevant chunks (user-scoped).
        float[] queryVector = embeddingModel.embed(question);
        List<ChunkSearchResult> chunks = chunkRepository.searchSimilar(
                userId,
                toVectorLiteral(queryVector),
                properties.getRag().getSimilarityThreshold(),
                properties.getRag().getTopK());

        saveMessage(conversation, userId, Message.ROLE_USER, question, null);

        if (chunks.isEmpty()) {
            saveMessage(conversation, userId, Message.ROLE_ASSISTANT, NO_CONTEXT_ANSWER, List.of());
            touch(conversation);
            return new ChatResponse(conversation.getId(), NO_CONTEXT_ANSWER, List.of());
        }

        // 2. Build prompt and call the LLM synchronously.
        String context = chunks.stream()
                .map(ChunkSearchResult::getContent)
                .collect(Collectors.joining("\n---\n"));
        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(context, question);

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .messages(history)
                .user(question)
                .call()
                .content();
        if (answer == null) {
            answer = "";
        }

        // 3. Persist the assistant turn with its sources.
        List<SourceItem> sources = chunks.stream()
                .map(c -> new SourceItem(
                        c.getChunkId(),
                        c.getDocumentId(),
                        c.getFilename(),
                        c.getScore() != null ? c.getScore() : 0.0,
                        c.getPageNum()))
                .toList();
        saveMessage(conversation, userId, Message.ROLE_ASSISTANT, answer, sources);
        touch(conversation);

        return new ChatResponse(conversation.getId(), answer, sources);
    }

    private Conversation resolveConversation(Long userId, String question, Long conversationId) {
        if (conversationId == null) {
            String title = question.length() > TITLE_MAX_LENGTH
                    ? question.substring(0, TITLE_MAX_LENGTH)
                    : question;
            return conversationRepository.save(Conversation.builder()
                    .userId(userId)
                    .title(title)
                    .build());
        }
        // Wrong owner -> 404, never 403 (D-03).
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new EntityNotFoundException("会话不存在"));
    }

    private List<org.springframework.ai.chat.messages.Message> loadHistory(Long conversationId, Long userId) {
        List<Message> recent = new ArrayList<>(
                messageRepository.findTop6ByConversationIdAndUserIdOrderByCreatedAtDesc(conversationId, userId));
        java.util.Collections.reverse(recent); // chronological order
        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>(HISTORY_LIMIT);
        for (Message m : recent) {
            if (Message.ROLE_ASSISTANT.equals(m.getRole())) {
                history.add(new AssistantMessage(m.getContent()));
            } else {
                history.add(new UserMessage(m.getContent()));
            }
        }
        return history;
    }

    private void saveMessage(Conversation conversation, Long userId, String role,
                             String content, List<SourceItem> sources) {
        messageRepository.save(Message.builder()
                .conversationId(conversation.getId())
                .userId(userId)
                .role(role)
                .content(content)
                .sources(sources != null ? toJson(sources) : null)
                .build());
    }

    private void touch(Conversation conversation) {
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);
    }

    private String toJson(List<SourceItem> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize sources", e);
            return "[]";
        }
    }

    /** pgvector text literal: "[v1,v2,...]". */
    private static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
