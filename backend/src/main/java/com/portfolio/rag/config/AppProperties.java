package com.portfolio.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Binds the custom {@code app.*} block from application.yml.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Rag rag = new Rag();
    private final Ai ai = new Ai();

    @Getter
    @Setter
    public static class Jwt {
        /** HMAC-SHA256 signing secret (>= 256 bits, ideally Base64-encoded). */
        private String secret;
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        private Duration refreshTokenTtl = Duration.ofDays(7);
    }

    @Getter
    @Setter
    public static class Rag {
        /** Chunk size in (approximate) tokens. */
        private int chunkSize = 512;
        /** Chunk overlap in (approximate) tokens. */
        private int chunkOverlap = 64;
        /** Number of chunks retrieved per question. */
        private int topK = 5;
        /** Minimum cosine similarity for a chunk to be used. */
        private double similarityThreshold = 0.75;
    }

    /**
     * All AI provider configuration lives here ({@code app.ai.*}) rather than under
     * Spring AI's {@code spring.ai.*} autoconfigure namespace, which we disable.
     */
    @Getter
    @Setter
    public static class Ai {
        private final Chat chat = new Chat();
        private final Embedding embedding = new Embedding();

        /** Per-provider credentials / model names. */
        private final Provider openai = new Provider();
        private final Provider groq = new Provider();
        private final Provider gemini = new Provider();

        @Getter
        @Setter
        public static class Chat {
            /**
             * Providers tried in order at runtime; the first that succeeds wins, the
             * rest are fallbacks. Valid values: {@code groq}, {@code gemini}, {@code openai}.
             */
            private List<String> providers = List.of("groq", "openai");
        }

        @Getter
        @Setter
        public static class Embedding {
            /**
             * Embedding provider, fixed at deploy time: {@code openai} (1536-dim) or
             * {@code gemini} (768-dim). Switching requires re-embedding every document.
             */
            private String provider = "openai";
            /** Vector dimension; must equal the DB schema's {@code vector(N)} column width. */
            private int dimensions = 1536;
        }

        /** OpenAI-compatible provider settings (OpenAI, Groq, Gemini all use this shape). */
        @Getter
        @Setter
        public static class Provider {
            private String apiKey;
            private String chatModel;
            private String embeddingModel;
            /** Base URL for OpenAI-compatible providers (Groq/Gemini); null = OpenAI default. */
            private String baseUrl;
        }
    }
}
