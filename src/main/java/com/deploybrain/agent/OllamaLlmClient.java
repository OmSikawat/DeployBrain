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
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class OllamaLlmClient implements LlmClient {

    private final RestTemplate ollamaRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ollama.base-url}")
    private String baseUrl;

    @Value("${ollama.model}")
    private String model;

    private static final Pattern TOOL_CALL_PATTERN =
            Pattern.compile("\\{\\s*\"tool_call\"\\s*:\\s*\\{.*?}\\s*}", Pattern.DOTALL);

    public OllamaLlmClient(@Qualifier("ollamaRestTemplate") RestTemplate ollamaRestTemplate, ObjectMapper objectMapper) {
        this.ollamaRestTemplate = ollamaRestTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isAvailable() {
        try {
            ResponseEntity<Map> response = ollamaRestTemplate.getForEntity(baseUrl + "/api/tags", Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResponse generateWithTools(String systemPrompt, List<Message> history, List<ToolDefinition> tools) {
        String url = baseUrl + "/api/chat";
        String fullSystemPrompt = appendToolInstructions(systemPrompt, tools);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", fullSystemPrompt));
        for (Message m : history) {
            String role = switch (m.getRole()) {
                case USER -> "user";
                case ASSISTANT -> "assistant";
                case TOOL_RESULT, SYSTEM -> "user";
            };
            String content = m.getContent();
            if (m.getRole() == Message.Role.TOOL_RESULT) {
                content = "Tool '" + m.getToolName() + "' returned:\n" + content;
            }
            messages.add(Map.of("role", role, "content", content));
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "stream", false
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = ollamaRestTemplate.postForEntity(url, request, Map.class);
            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("Ollama call failed: {}", e.getMessage());
            throw new IllegalStateException("Ollama call failed: " + e.getMessage(), e);
        }
    }

    private String appendToolInstructions(String systemPrompt, List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return systemPrompt;
        }
        StringBuilder sb = new StringBuilder(systemPrompt);
        sb.append("\n\nAVAILABLE TOOLS (text-based tool calling)\n");
        sb.append("You do not have native function calling. To call a tool, respond with ONLY ");
        sb.append("a JSON object in exactly this shape, and nothing else:\n");
        sb.append("{\"tool_call\": {\"name\": \"<tool_name>\", \"input\": {<parameters>}}}\n\n");
        sb.append("Tools available:\n");
        for (ToolDefinition t : tools) {
            sb.append("- ").append(t.getName()).append(": ").append(t.getDescription()).append("\n");
            sb.append("  input schema: ").append(t.getInputSchema()).append("\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private LlmResponse parseResponse(Map<String, Object> body) {
        Map<String, Object> message = (Map<String, Object>) body.get("message");
        String content = (String) message.get("content");

        Matcher matcher = TOOL_CALL_PATTERN.matcher(content);
        if (matcher.find()) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(matcher.group(), Map.class);
                Map<String, Object> toolCall = (Map<String, Object>) parsed.get("tool_call");
                return LlmResponse.builder()
                        .type(LlmResponse.ResponseType.TOOL_CALL)
                        .toolName((String) toolCall.get("name"))
                        .toolInput((Map<String, Object>) toolCall.getOrDefault("input", Map.of()))
                        .build();
            } catch (Exception e) {
                log.warn("Ollama response looked like a tool call but failed to parse - treating as final answer: {}", e.getMessage());
            }
        }

        return LlmResponse.builder().type(LlmResponse.ResponseType.FINAL_ANSWER).textContent(content).build();
    }
}