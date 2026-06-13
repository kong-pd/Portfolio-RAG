package com.portfolio.rag.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Routes a chat request across the configured providers in priority order,
 * falling back to the next provider whenever one fails. The provider list and
 * the matching {@link ChatClient} list are built once at startup by
 * {@code AiRoutingConfig}; this router only orchestrates the runtime fallback.
 */
@Slf4j
public class ChatRouter {

    private final List<ChatClient> clients;
    private final List<String> providerNames;

    public ChatRouter(List<ChatClient> clients, List<String> providerNames) {
        this.clients = clients;
        this.providerNames = providerNames;
    }

    /**
     * Tries each configured provider in order and returns the first successful
     * response. Throws {@link RuntimeException} only when every provider fails.
     */
    public ChatResponse chat(String systemPrompt, List<Message> history, String userMessage) {
        if (clients.isEmpty()) {
            throw new RuntimeException("No AI chat providers are configured. "
                    + "Set GROQ_API_KEY, OPENAI_API_KEY, or GOOGLE_API_KEY.");
        }
        Exception lastException = null;
        for (int i = 0; i < clients.size(); i++) {
            String name = i < providerNames.size() ? providerNames.get(i) : "provider-" + i;
            try {
                log.debug("Attempting chat via provider: {}", name);
                ChatResponse response = clients.get(i).prompt()
                        .system(systemPrompt)
                        .messages(history)
                        .user(userMessage)
                        .call()
                        .chatResponse();
                log.debug("Chat succeeded via provider: {}", name);
                return response;
            } catch (Exception e) {
                log.warn("Chat provider {} failed: {}. Trying next.", name, e.getMessage());
                lastException = e;
            }
        }
        throw new RuntimeException("All AI chat providers failed. Last error: "
                + (lastException != null ? lastException.getMessage() : "unknown"), lastException);
    }

    /**
     * Streams tokens from the first available provider, falling back to the next on
     * error. Subscription is offloaded to {@code Schedulers.boundedElastic()} so the
     * caller (Tomcat request thread) is never blocked waiting for the first token.
     */
    public Flux<String> stream(String systemPrompt, List<Message> history, String userMessage) {
        if (clients.isEmpty()) {
            return Flux.error(new RuntimeException(
                    "No AI chat providers are configured. Set GROQ_API_KEY or GOOGLE_API_KEY."));
        }
        Flux<String> chain = buildAttempt(0, systemPrompt, history, userMessage);
        for (int i = 1; i < clients.size(); i++) {
            final int idx = i;
            chain = chain.onErrorResume(e -> {
                log.warn("Stream provider '{}' failed: {}. Falling back to '{}'.",
                        safeProviderName(idx - 1), e.getMessage(), safeProviderName(idx));
                return buildAttempt(idx, systemPrompt, history, userMessage);
            });
        }
        return chain.subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<String> buildAttempt(int idx, String systemPrompt,
                                      List<Message> history, String userMessage) {
        String name = safeProviderName(idx);
        return clients.get(idx).prompt()
                .system(systemPrompt)
                .messages(history)
                .user(userMessage)
                .stream()
                .content()
                .doOnSubscribe(s -> log.debug("Streaming via provider: {}", name));
    }

    private String safeProviderName(int idx) {
        return idx < providerNames.size() ? providerNames.get(idx) : "provider-" + idx;
    }
}
