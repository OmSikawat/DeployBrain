package com.deploybrain.config;

import com.deploybrain.agent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmClientFactory {

    @Value("${llm.primary}")
    private String primaryName;

    @Value("${llm.fallback}")
    private String fallbackName;

    @Bean
    public LlmClient llmClient(GeminiLlmClient gemini, GroqLlmClient groq, OllamaLlmClient ollama) {
        LlmClient primary = resolve(primaryName, gemini, groq, ollama);
        LlmClient fallback = resolve(fallbackName, gemini, groq, ollama);
        return new FallbackLlmClient(primary, fallback);
    }

    private LlmClient resolve(String name, GeminiLlmClient gemini, GroqLlmClient groq, OllamaLlmClient ollama) {
        return switch (name.toLowerCase()) {
            case "gemini" -> gemini;
            case "groq" -> groq;
            case "ollama" -> ollama;
            default -> throw new IllegalArgumentException("Unknown LLM provider configured: " + name);
        };
    }
}