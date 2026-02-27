package com.aegis.orderservice.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory token bucket when Redis is not available. Per-bucket key; not shared across instances.
 */
@Component
@ConditionalOnMissingBean(RateLimiter.class)
public class InMemoryTokenBucketRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public TokenBucketResult tryConsume(String bucketKey, int capacity, double refillPerSecond) {
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> new Bucket(capacity, refillPerSecond));
        return bucket.tryConsume(capacity, refillPerSecond);
    }

    private static class Bucket {
        private double tokens;
        private long lastRefillMs;
        private final ReentrantLock lock = new ReentrantLock();

        Bucket(int capacity, double refillPerSecond) {
            this.tokens = capacity;
            this.lastRefillMs = System.currentTimeMillis();
        }

        TokenBucketResult tryConsume(int capacity, double refillPerSecond) {
            lock.lock();
            try {
                long now = System.currentTimeMillis();
                double elapsed = (now - lastRefillMs) / 1000.0;
                tokens = Math.min(capacity, tokens + elapsed * refillPerSecond);
                lastRefillMs = now;
                if (tokens >= 1) {
                    tokens -= 1;
                    return TokenBucketResult.allowed();
                }
                int retryAfter = (int) Math.ceil((1 - tokens) / refillPerSecond);
                return TokenBucketResult.rejected(Math.max(1, retryAfter));
            } finally {
                lock.unlock();
            }
        }
    }
}
