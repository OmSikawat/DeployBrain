package com.deploybrain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    private static final int EXPECTED_DIMENSIONS = 768;

    private final RestTemplate geminiRestTemplate;

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.base-url}")
    private String baseUrl;

    @Value("${gemini.embedding-model}")
    private String embeddingModel;

    public EmbeddingService(@Qualifier("geminiRestTemplate") RestTemplate geminiRestTemplate) {
        this.geminiRestTemplate = geminiRestTemplate;
    }

    /**
     * Embeds text into a 768-dimensional vector using Gemini's
     * text-embedding-004 model. Returns null (never throws) on blank input,
     * API failure, or unexpected dimension count - callers treat null as
     * "skip this one" per the partial-success policy.
     */
    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Skipping embedding for blank/empty text");
            return null;
        }

        String url = String.format("%s/v1beta/models/%s:embedContent?key=%s",
                baseUrl, embeddingModel, apiKey);

        Map<String, Object> requestBody = Map.of(
                "model", "models/" + embeddingModel,
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "outputDimensionality", EXPECTED_DIMENSIONS
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = geminiRestTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null || !body.containsKey("embedding")) {
                log.error("Gemini embedding response missing 'embedding' field");
                return null;
            }

            Map<String, Object> embeddingObj = (Map<String, Object>) body.get("embedding");
            List<Double> values = (List<Double>) embeddingObj.get("values");

            if (values == null || values.size() != EXPECTED_DIMENSIONS) {
                log.error("Gemini embedding returned {} dimensions, expected {} - rejecting",
                        values == null ? 0 : values.size(), EXPECTED_DIMENSIONS);
                return null;
            }

            float[] vector = new float[EXPECTED_DIMENSIONS];
            for (int i = 0; i < EXPECTED_DIMENSIONS; i++) {
                vector[i] = values.get(i).floatValue();
            }
            return vector;

        } catch (Exception e) {
            log.error("Failed to embed text via Gemini API: {}", e.getMessage());
            return null;
        }
    }
}