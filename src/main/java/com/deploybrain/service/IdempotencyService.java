package com.deploybrain.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class IdempotencyService {

    private static final String DELIVERY_KEY_PREFIX = "webhook:delivery:";
    private static final Duration DELIVERY_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;

    public IdempotencyService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Returns true if this is the FIRST time this delivery ID has been
     * seen (safe to process). Returns false if it's a duplicate delivery
     * (GitHub retried the same webhook) - caller should return 200
     * immediately without reprocessing.
     */
    public boolean isFirstDelivery(String deliveryId) {
        if (deliveryId == null) return true; // no ID to dedupe on, proceed
        String key = DELIVERY_KEY_PREFIX + deliveryId;
        Boolean wasAbsent = redisTemplate.opsForValue().setIfAbsent(key, "seen", DELIVERY_TTL);
        return Boolean.TRUE.equals(wasAbsent);
    }
}