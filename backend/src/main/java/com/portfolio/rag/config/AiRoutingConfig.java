package com.portfolio.rag.config;

import com.portfolio.rag.ai.ChatRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/**
 * Manually wires the AI chat / embedding beans from {@code app.ai.*}.
 *
 * <p>Spring AI's OpenAI autoconfigure is excluded (see pom.xml) so that we can:
 * <ul>
 *   <li>build a {@link ChatRouter} that tries each chat provider in priority order
 *       (Groq, Gemini, OpenAI) and falls back on failure, and</li>
 *   <li>pin a single embedding provider/dimension at deploy time.</li>
 * </ul>
 *
 * <p>OpenAI, Groq and Gemini are all reached through Spring AI's OpenAI client:
 * Groq and Gemini both expose OpenAI-compatible REST endpoints, so a per-provider
 * {@code base-url} is all that differs. (Spring AI 1.0.0 has no api-key-based Google
 * GenAI starter; its Gemini support is Vertex-AI/GCP-credential based, which a simple
 * {@code GOOGLE_API_KEY} deployment cannot use.)
 */
@Slf4j
@Configuration
public class AiRoutingConfig {

    private static final String DEFAULT_OPENAI_CHAT_MODEL = "gpt-4o";
    private static final String DEFAULT_OPENAI_EMBEDDING_MODEL = "text-embedding-3-small";
    private static final String DEFAULT_GROQ_BASE_URL = "https://api.groq.com/openai";
    private static final String DEFAULT_GROQ_CHAT_MODEL = "llama-3.3-70b-versatile";
    private static final String DEFAULT_GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/openai";
    private static final String DEFAULT_GEMINI_CHAT_MODEL = "gemini-2.0-flash";
    private static final String DEFAULT_GEMINI_EMBEDDING_MODEL = "text-embedding-004";

    private final AppProperties.Ai ai;

    public AiRoutingConfig(AppProperties appProperties) {
        this.ai = appProperties.getAi();
    }

    @Bean
    @Primary
    public ChatRouter chatRouter() {
        List<ChatClient> clients = new ArrayList<>();
        List<String> activeProviders = new ArrayList<>();
        for (String provider : ai.getChat().getProviders()) {
            try {
                ChatClient client = buildChatClient(provider);
                if (client != null) {
                    clients.add(client);
                    activeProviders.add(provider.toLowerCase());
                } else {
                    log.warn("Chat provider '{}' is listed but has no API key configured; skipping.",
                            provider);
                }
            } catch (Exception e) {
                log.warn("Chat provider '{}' could not be initialised: {}; skipping.",
                        provider, e.getMessage());
            }
        }
        if (clients.isEmpty()) {
            log.warn("No AI chat provider is reachable at startup (all API keys are blank). "
                    + "Set GROQ_API_KEY, OPENAI_API_KEY, or GOOGLE_API_KEY before making chat requests.");
        } else {
            log.info("Chat routing active. Provider order (with fallback): {}", activeProviders);
        }
        return new ChatRouter(clients, activeProviders);
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        String provider = ai.getEmbedding().getProvider();
        int configuredDims = ai.getEmbedding().getDimensions();

        EmbeddingModel model = buildEmbeddingModel(provider);

        int expected = expectedDimensions(provider);
        if (expected != configuredDims) {
            throw new IllegalStateException(String.format(
                    "Embedding dimension mismatch: provider '%s' produces %d-dim vectors but "
                            + "app.ai.embedding.dimensions=%d. This must equal the DB vector(N) column "
                            + "width. Fix the config (and re-embed all documents) before starting.",
                    provider, expected, configuredDims));
        }
        log.info("Embedding model active: provider='{}', dimensions={}", provider, configuredDims);
        return model;
    }

    /** Known native output dimension for each embedding provider. */
    private int expectedDimensions(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> 1536; // text-embedding-3-small
            case "gemini" -> 768;  // text-embedding-004
            default -> throw new IllegalStateException(
                    "Unknown embedding provider '" + provider + "'. Use 'openai' or 'gemini'.");
        };
    }

    private ChatClient buildChatClient(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> buildOpenAiCompatChatClient(
                    ai.getOpenai(), null, DEFAULT_OPENAI_CHAT_MODEL);
            case "groq" -> buildOpenAiCompatChatClient(
                    ai.getGroq(), DEFAULT_GROQ_BASE_URL, DEFAULT_GROQ_CHAT_MODEL);
            case "gemini" -> buildOpenAiCompatChatClient(
                    ai.getGemini(), DEFAULT_GEMINI_BASE_URL, DEFAULT_GEMINI_CHAT_MODEL);
            default -> {
                log.warn("Unknown chat provider '{}'; skipping.", provider);
                yield null;
            }
        };
    }

    /**
     * Builds a {@link ChatClient} backed by Spring AI's OpenAI client. Returns
     * {@code null} when no API key is set so the provider is silently skipped.
     */
    private ChatClient buildOpenAiCompatChatClient(AppProperties.Ai.Provider cfg,
                                                   String defaultBaseUrl,
                                                   String defaultModel) {
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            return null;
        }
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(cfg.getApiKey());
        String baseUrl = cfg.getBaseUrl() != null ? cfg.getBaseUrl() : defaultBaseUrl;
        if (baseUrl != null) {
            apiBuilder.baseUrl(baseUrl);
        }
        OpenAiApi api = apiBuilder.build();

        String model = cfg.getChatModel() != null ? cfg.getChatModel() : defaultModel;
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .build();
        return ChatClient.create(chatModel);
    }

    private EmbeddingModel buildEmbeddingModel(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> buildOpenAiCompatEmbeddingModel(
                    ai.getOpenai(), null, DEFAULT_OPENAI_EMBEDDING_MODEL);
            case "gemini" -> buildOpenAiCompatEmbeddingModel(
                    ai.getGemini(), DEFAULT_GEMINI_BASE_URL, DEFAULT_GEMINI_EMBEDDING_MODEL);
            default -> throw new IllegalStateException(
                    "Unknown embedding provider '" + provider + "'. Use 'openai' or 'gemini'.");
        };
    }

    private EmbeddingModel buildOpenAiCompatEmbeddingModel(AppProperties.Ai.Provider cfg,
                                                           String defaultBaseUrl,
                                                           String defaultModel) {
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "Embedding provider has no API key configured. Set the corresponding "
                            + "app.ai.<provider>.api-key.");
        }
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(cfg.getApiKey());
        String baseUrl = cfg.getBaseUrl() != null ? cfg.getBaseUrl() : defaultBaseUrl;
        if (baseUrl != null) {
            apiBuilder.baseUrl(baseUrl);
        }
        OpenAiApi api = apiBuilder.build();

        String model = cfg.getEmbeddingModel() != null ? cfg.getEmbeddingModel() : defaultModel;
        return new OpenAiEmbeddingModel(
                api,
                org.springframework.ai.document.MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(model).build());
    }
}
