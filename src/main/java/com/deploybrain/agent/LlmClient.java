package com.deploybrain.agent;

import java.util.List;

public interface LlmClient {
    LlmResponse generateWithTools(String systemPrompt, List<Message> history, List<ToolDefinition> tools);
    boolean isAvailable();
    String getProviderName();
}