package com.portfolio.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Binds the custom {@code app.*} block from application.yml.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Rag rag = new Rag();

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
}
