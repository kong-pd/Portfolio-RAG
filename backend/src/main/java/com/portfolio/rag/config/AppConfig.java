package com.portfolio.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables async execution (document ingestion) and binds {@link AppProperties}.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
}
