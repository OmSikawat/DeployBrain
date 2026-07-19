package com.deploybrain.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class LlmConfig {

    @Bean
    public RestTemplate groqRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public RestTemplate ollamaRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(600)) // CPU-only 8B model needs real headroom, especially cold-started
                .build();
    }
}