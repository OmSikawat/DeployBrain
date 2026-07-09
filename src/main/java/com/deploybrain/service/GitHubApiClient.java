package com.deploybrain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class GitHubApiClient {

    private final RestTemplate githubRestTemplate;

    public GitHubApiClient(@Qualifier("githubRestTemplate") RestTemplate githubRestTemplate) {
        this.githubRestTemplate = githubRestTemplate;
    }

    public byte[] downloadLogZip(String logsUrl) {
        ResponseEntity<byte[]> response = githubRestTemplate.exchange(
                logsUrl,
                HttpMethod.GET,
                null,
                byte[].class
        );

        checkRateLimit(response);

        if (response.getBody() == null || response.getBody().length == 0) {
            throw new IllegalStateException("GitHub returned an empty log archive for URL: " + logsUrl);
        }

        return response.getBody();
    }

    private void checkRateLimit(ResponseEntity<?> response) {
        String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
        if (remaining != null) {
            try {
                int remainingCalls = Integer.parseInt(remaining);
                if (remainingCalls < 100) {
                    log.warn("GitHub API rate limit low: {} calls remaining", remainingCalls);
                }
            } catch (NumberFormatException ignored) {
                // header format unexpected, skip check silently
            }
        }
    }
}