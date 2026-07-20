package com.deploybrain.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate mlServiceRestTemplate;

    @Value("${ml.service.base-url}")
    private String mlServiceBaseUrl;

    public HealthController(JdbcTemplate jdbcTemplate, RedisTemplate<String, String> redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.mlServiceRestTemplate = new RestTemplate();
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("postgres", checkPostgres());
        status.put("redis", checkRedis());
        status.put("ml_service", checkMlService());
        return status;
    }

    private String checkPostgres() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            log.warn("Postgres health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String checkRedis() {
        try {
            redisTemplate.opsForValue().get("__health_check__");
            return "UP";
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String checkMlService() {
        try {
            mlServiceRestTemplate.getForObject(mlServiceBaseUrl + "/health", String.class);
            return "UP";
        } catch (Exception e) {
            log.warn("ML service health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }
}