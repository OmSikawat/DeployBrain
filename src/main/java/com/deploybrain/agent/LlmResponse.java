package com.deploybrain.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmResponse {
    public enum ResponseType { TOOL_CALL, FINAL_ANSWER }

    private ResponseType type;
    private String toolName;
    private Map<String, Object> toolInput;
    private String textContent;
    private String providerUsed;
}