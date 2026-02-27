package com.aegis.orderservice.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenBucketResult {

    private final boolean allowed;
    /** Seconds after which the client may retry; only meaningful when !allowed */
    private final int retryAfterSeconds;

    public static TokenBucketResult allowed() {
        return new TokenBucketResult(true, 0);
    }

    public static TokenBucketResult rejected(int retryAfterSeconds) {
        return new TokenBucketResult(false, Math.max(1, retryAfterSeconds));
    }
}
