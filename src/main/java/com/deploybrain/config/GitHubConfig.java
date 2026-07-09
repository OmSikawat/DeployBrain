package com.deploybrain.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class GitHubConfig {

    @Value("${github.token}")
    private String githubToken;

    @Bean
    public RestTemplate githubRestTemplate(RestTemplateBuilder builder) {
        ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            request.getHeaders().add("Authorization", "Bearer " + githubToken);
            request.getHeaders().add("Accept", "application/vnd.github+json");
            request.getHeaders().add("X-GitHub-Api-Version", "2022-11-28");
            return execution.execute(request, body);
        };

        return builder
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(60))
                .additionalInterceptors(authInterceptor)
                .build();
    }
}