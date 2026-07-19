package com.deploybrain.agent;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class FallbackLlmClient implements LlmClient {

    private final LlmClient primary;
    private final LlmClient fallback;

    public FallbackLlmClient(LlmClient primary, LlmClient fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public LlmResponse generateWithTools(String systemPrompt, List<Message> history, List<ToolDefinition> tools) {
        if (primary.isAvailable()) {
            try {
                LlmResponse response = primary.generateWithTools(systemPrompt, history, tools);
                response.setProviderUsed(primary.getProviderName());
                return response;
            } catch (Exception e) {
                log.warn("Primary LLM provider '{}' failed ({}) - falling back to '{}'",
                        primary.getProviderName(), e.getMessage(), fallback.getProviderName());
            }
        } else {
            log.warn("Primary LLM provider '{}' reported unavailable - using fallback '{}' directly",
                    primary.getProviderName(), fallback.getProviderName());
        }

        LlmResponse response = fallback.generateWithTools(systemPrompt, history, tools);
        response.setProviderUsed(fallback.getProviderName());
        return response;
    }

    @Override
    public boolean isAvailable() {
        return primary.isAvailable() || fallback.isAvailable();
    }

    @Override
    public String getProviderName() {
        return primary.getProviderName() + "/" + fallback.getProviderName();
    }
}