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
public class GroqLlmClient implements LlmClient {

    private final RestTemplate groqRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api-key}")
    private String apiKey;

    @Value("${groq.model}")
    private String model;

    @Value("${groq.base-url}")
    private String baseUrl;

    private volatile boolean unavailable = false;

    public GroqLlmClient(@Qualifier("groqRestTemplate") RestTemplate groqRestTemplate, ObjectMapper objectMapper) {
        this.groqRestTemplate = groqRestTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isAvailable() {
        return !unavailable;
    }

    @Override
    public String getProviderName() {
        return "groq";
    }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResponse generateWithTools(String systemPrompt, List<Message> history, List<ToolDefinition> tools) {
        String url = baseUrl + "/chat/completions";

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
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

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", buildToolsArray(tools));
            requestBody.put("tool_choice", "auto");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = groqRestTemplate.postForEntity(url, request, Map.class);
            unavailable = false;
            return parseResponse(response.getBody());
        } catch (HttpClientErrorException.TooManyRequests e) {
            // Do NOT mark unavailable on 429. Let FallbackLlmClient switch to backup for this turn,
            // but keep Groq available for the next turn since rate limits are temporary.
            log.warn("Groq reported 429 TOO_MANY_REQUESTS - falling back temporarily");
            throw new IllegalStateException("Groq rate limited: " + e.getMessage(), e);
        } catch (HttpServerErrorException.ServiceUnavailable | org.springframework.web.client.ResourceAccessException e) {
            unavailable = true;
            log.warn("Groq reported {} - marking permanently unavailable for fallback", e.getMessage());
            throw new IllegalStateException("Groq unavailable: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Groq call failed: {}", e.getMessage());
            throw new IllegalStateException("Groq call failed: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> buildToolsArray(List<ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolDefinition t : tools) {
            result.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", t.getName(),
                            "description", t.getDescription(),
                            "parameters", t.getInputSchema()
                    )
            ));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private LlmResponse parseResponse(Map<String, Object> body) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");

        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            Map<String, Object> firstCall = toolCalls.get(0);
            Map<String, Object> function = (Map<String, Object>) firstCall.get("function");
            String argsJson = (String) function.get("arguments");
            Map<String, Object> args;
            try {
                args = objectMapper.readValue(argsJson, Map.class);
            } catch (Exception e) {
                log.error("Failed to parse Groq tool_call arguments JSON: {}", argsJson);
                args = Map.of();
            }
            return LlmResponse.builder()
                    .type(LlmResponse.ResponseType.TOOL_CALL)
                    .toolName((String) function.get("name"))
                    .toolInput(args)
                    .build();
        }

        String content = (String) message.get("content");
        return LlmResponse.builder().type(LlmResponse.ResponseType.FINAL_ANSWER).textContent(content).build();
    }
}