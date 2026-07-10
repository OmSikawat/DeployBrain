package com.deploybrain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class BuildStateService {

    private static final String PROCESSING_KEY_PREFIX = "build:processing:";
    private static final Duration PROCESSING_TTL = Duration.ofHours(1);

    private final RedisTemplate<String, String> redisTemplate;

    public BuildStateService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Atomically claims a build for processing using Redis's SETNX semantics
     * (setIfAbsent). Returns true if THIS call successfully claimed the
     * build - safe to proceed. Returns false if another delivery already
     * claimed it - this is a duplicate event, skip it.
     *
     * This is atomic at the Redis level, closing the race window where two
     * near-simultaneous duplicate deliveries could both pass a plain GET
     * check before either had written a SET.
     *
     * Deliberately does NOT catch Redis connection exceptions here. If Redis
     * is down, this throws, which propagates up through the @KafkaListener
     * method in BuildEventConsumer, which in turn triggers KafkaConfig's
     * DefaultErrorHandler retry logic - so a Redis outage causes a retry,
     * not a silently lost event.
     */
    public boolean tryMarkProcessing(UUID buildId) {
        String key = PROCESSING_KEY_PREFIX + buildId;
        Boolean wasAbsent = redisTemplate.opsForValue()
                .setIfAbsent(key, "PROCESSING", PROCESSING_TTL);
        return Boolean.TRUE.equals(wasAbsent);
    }

    public void markProcessed(UUID buildId) {
        String key = PROCESSING_KEY_PREFIX + buildId;
        redisTemplate.opsForValue().set(key, "PROCESSED", PROCESSING_TTL);
    }
}