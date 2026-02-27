package com.aegis.orderservice.ratelimit;

public interface RateLimiter {

    TokenBucketResult tryConsume(String bucketKey, int capacity, double refillPerSecond);
}
