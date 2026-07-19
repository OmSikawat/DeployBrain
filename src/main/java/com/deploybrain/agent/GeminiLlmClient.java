package com.deploybrain.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
public class GeminiLlmClient implements LlmClient {

    private final RestTemplate geminiRestTemplate;

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.base-url}")
    private String baseUrl;

    private volatile boolean unavailable = false;

    public GeminiLlmClient(@Qualifier("geminiRestTemplate") RestTemplate geminiRestTemplate) {
        this.geminiRestTemplate = geminiRestTemplate;
    }

    @Override
    public boolean isAvailable() {
        return !unavailable;
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResponse generateWithTools(String systemPrompt, List<Message> history, List<ToolDefinition> tools) {
        String url = String.format("%s/v1beta/models/%s:generateContent?key=%s", baseUrl, model, apiKey);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        requestBody.put("contents", buildContents(history));
        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", List.of(Map.of("functionDeclarations", buildFunctionDeclarations(tools))));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = geminiRestTemplate.postForEntity(url, request, Map.class);
            unavailable = false;
            return parseResponse(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            log.warn("Gemini reported 429 TOO_MANY_REQUESTS - falling back temporarily");
            throw new IllegalStateException("Gemini rate limited: " + e.getMessage(), e);
        } catch (org.springframework.web.client.HttpServerErrorException.ServiceUnavailable | org.springframework.web.client.ResourceAccessException e) {
            unavailable = true;
            log.warn("Gemini reported {} - marking permanently unavailable for fallback", e.getMessage());
            throw new IllegalStateException("Gemini unavailable: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Gemini call failed: {}", e.getMessage());
            throw new IllegalStateException("Gemini call failed: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> buildContents(List<Message> history) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (Message m : history) {
            String role = switch (m.getRole()) {
                case USER -> "user";
                case ASSISTANT -> "model";
                case TOOL_RESULT, SYSTEM -> "user";
            };
            String text = m.getContent();
            if (m.getRole() == Message.Role.TOOL_RESULT) {
                text = "Tool '" + m.getToolName() + "' returned:\n" + text;
            }
            contents.add(Map.of("role", role, "parts", List.of(Map.of("text", text))));
        }
        return contents;
    }

    private List<Map<String, Object>> buildFunctionDeclarations(List<ToolDefinition> tools) {
        List<Map<String, Object>> declarations = new ArrayList<>();
        for (ToolDefinition t : tools) {
            declarations.add(Map.of(
                    "name", t.getName(),
                    "description", t.getDescription(),
                    "parameters", t.getInputSchema()
            ));
        }
        return declarations;
    }

//    @SuppressWarnings("unchecked")
//    private LlmResponse parseResponse(Map<String, Object> body) {
//        List<Map<String, Object>> candidates = (List<Map<String, Object>>) body.get("candidates");
//        if (candidates == null || candidates.isEmpty()) {
//            return LlmResponse.builder().type(LlmResponse.ResponseType.FINAL_ANSWER)
//                    .textContent("{\"action\":\"needs_review\",\"diagnosis\":\"No response from model\",\"reason\":\"Empty candidates list\"}")
//                    .build();
//        }
//        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
//        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
//
//        for (Map<String, Object> part : parts) {
//            if (part.containsKey("functionCall")) {
//                Map<String, Object> fc = (Map<String, Object>) part.get("functionCall");
//                return LlmResponse.builder()
//                        .type(LlmResponse.ResponseType.TOOL_CALL)
//                        .toolName((String) fc.get("name"))
//                        .toolInput((Map<String, Object>) fc.getOrDefault("args", Map.of()))
//                        .build();
//            }
//        }
//        StringBuilder text = new StringBuilder();
//        for (Map<String, Object> part : parts) {
//            if (part.containsKey("text")) {
//                text.append((String) part.get("text"));
//            }
//        }
//        return LlmResponse.builder().type(LlmResponse.ResponseType.FINAL_ANSWER).textContent(text.toString()).build();
//    }
@SuppressWarnings("unchecked")
private LlmResponse parseResponse(Map<String, Object> body) {
    List<Map<String, Object>> candidates = (List<Map<String, Object>>) body.get("candidates");
    if (candidates == null || candidates.isEmpty()) {
        return LlmResponse.builder().type(LlmResponse.ResponseType.FINAL_ANSWER)
                .textContent("{\"action\":\"needs_review\",\"diagnosis\":\"No response from model\",\"reason\":\"Empty candidates list\"}")
                .build();
    }
    Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

    for (Map<String, Object> part : parts) {
        if (part.containsKey("functionCall")) {
            Map<String, Object> fc = (Map<String, Object>) part.get("functionCall");
            return LlmResponse.builder()
                    .type(LlmResponse.ResponseType.TOOL_CALL)
                    .toolName((String) fc.get("name"))
                    .toolInput((Map<String, Object>) fc.getOrDefault("args", Map.of()))
                    .build();
        }
    }

    StringBuilder text = new StringBuilder();
    for (Map<String, Object> part : parts) {
        if (part.containsKey("text")) {
            text.append((String) part.get("text"));
        }
    }
    String textContent = text.toString();

    // FIX: Gemini sometimes emits tool arguments as raw JSON text instead
    // of a proper functionCall part, especially as conversation history
    // grows. If the text looks like {"key": "value", ...} WITHOUT an
    // "action" field, it's almost certainly leaked tool-call arguments,
    // not a real final answer - previously this fell through and was
    // misinterpreted as a final answer with no "action"/"diagnosis" key,
    // producing "No diagnosis provided".
    String recoveredToolCall = tryRecoverLeakedToolCall(textContent);
    if (recoveredToolCall != null) {
        log.warn("Detected leaked tool-call arguments in Gemini text response (missing proper functionCall wrapper) - recovering as tool call");
        return LlmResponse.builder()
                .type(LlmResponse.ResponseType.TOOL_CALL)
                .toolName(null) // caller must infer - see AgentOrchestrator change below
                .toolInput(parseJsonSafely(recoveredToolCall))
                .textContent(recoveredToolCall)
                .build();
    }

    return LlmResponse.builder().type(LlmResponse.ResponseType.FINAL_ANSWER).textContent(textContent).build();
}

    @SuppressWarnings("unchecked")
    private String tryRecoverLeakedToolCall(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null;

        try {
            Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(trimmed, Map.class);
            // A real final answer always has "action". Leaked tool args never do.
            if (!parsed.containsKey("action")) {
                return trimmed;
            }
        } catch (Exception ignored) {
            // not valid JSON at all - definitely not a leaked tool call, treat as normal text
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonSafely(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}