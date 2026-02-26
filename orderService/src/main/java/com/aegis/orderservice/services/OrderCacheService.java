package com.aegis.orderservice.services;

import com.aegis.orderservice.dto.OrderResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cache-aside for single-order reads. Key: order:{orderId}. Lock: lock:order:{orderId}.
 * TTL 5–15 minutes with jitter. Stampede: short lock so one thread populates, others wait or fallback to DB.
 */
@Service
@ConditionalOnBean(org.springframework.data.redis.connection.RedisConnectionFactory.class)
public class OrderCacheService {

    private static final Logger log = LoggerFactory.getLogger(OrderCacheService.class);
    private static final String KEY_PREFIX = "order:";
    private static final String LOCK_PREFIX = "lock:order:";
    private static final long TTL_MIN_SECONDS = 5 * 60;   // 5 min
    private static final long TTL_MAX_SECONDS = 15 * 60;  // 15 min
    private static final long LOCK_PX_MS = 5_000;         // 5s lock
    private static final long WAIT_AFTER_LOCK_MISS_MS = 200;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public OrderCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Optional<OrderResponse> get(UUID orderId) {
        String key = KEY_PREFIX + orderId;
        String raw = redis.opsForValue().get(key);
        if (raw != null) {
            return parseOrderResponse(raw);
        }
        return Optional.empty();
    }

    public void set(UUID orderId, OrderResponse response) {
        String key = KEY_PREFIX + orderId;
        long ttlSeconds = ttlWithJitter();
        try {
            String json = objectMapper.writeValueAsString(response);
            redis.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize order for cache: orderId={}", orderId, e);
        }
    }

    public void invalidate(UUID orderId) {
        String key = KEY_PREFIX + orderId;
        Boolean removed = redis.delete(key);
        if (Boolean.TRUE.equals(removed)) {
            log.debug("Cache invalidated for orderId={}", orderId);
        }
    }

    /**
     * Try to acquire lock. Returns true if lock was acquired (caller must release with releaseLock).
     */
    public boolean tryLock(UUID orderId) {
        String lockKey = LOCK_PREFIX + orderId;
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMillis(LOCK_PX_MS)));
    }

    public void releaseLock(UUID orderId) {
        String lockKey = LOCK_PREFIX + orderId;
        redis.delete(lockKey);
    }

    public long getWaitAfterLockMissMs() {
        return WAIT_AFTER_LOCK_MISS_MS;
    }

    private Optional<OrderResponse> parseOrderResponse(String raw) {
        try {
            OrderResponse r = objectMapper.readValue(raw, OrderResponse.class);
            return Optional.of(r);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse cached order response", e);
            return Optional.empty();
        }
    }

    private static long ttlWithJitter() {
        long range = TTL_MAX_SECONDS - TTL_MIN_SECONDS;
        return TTL_MIN_SECONDS + (range > 0 ? ThreadLocalRandom.current().nextLong(0, range + 1) : 0);
    }
}
