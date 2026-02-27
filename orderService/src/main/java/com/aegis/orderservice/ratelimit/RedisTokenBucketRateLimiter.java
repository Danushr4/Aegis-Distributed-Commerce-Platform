package com.aegis.orderservice.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token-bucket rate limiter using Redis and a Lua script for atomicity.
 * Keys: {key} -> tokens (double as string), {key}:ts -> last refill time (ms).
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(org.springframework.data.redis.connection.RedisConnectionFactory.class)
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private static final String SCRIPT = """
        local k = KEYS[1]
        local kts = k .. ':ts'
        local capacity = tonumber(ARGV[1])
        local refillPerSec = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        local tokens = tonumber(redis.call('GET', k) or capacity)
        local ts = tonumber(redis.call('GET', kts) or now)
        local elapsed = (now - ts) / 1000.0
        tokens = math.min(capacity, tokens + elapsed * refillPerSec)
        if tokens >= 1 then
          tokens = tokens - 1
          redis.call('SET', k, tostring(tokens))
          redis.call('SET', kts, tostring(now))
          return {1, 0}
        else
          local retryAfter = math.ceil((1 - tokens) / refillPerSec)
          if retryAfter < 1 then retryAfter = 1 end
          return {0, retryAfter}
        end
        """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>(SCRIPT, List.class);
    }

    /**
     * Try to consume one token. Returns allowed + retry-after seconds if rejected.
     */
    @SuppressWarnings("unchecked")
    public TokenBucketResult tryConsume(String key, int capacity, double refillPerSecond) {
        long now = System.currentTimeMillis();
        List<Long> result = redis.execute(
                script,
                List.of(key),
                String.valueOf(capacity),
                String.valueOf(refillPerSecond),
                String.valueOf(now));
        if (result == null || result.size() < 2) {
            return TokenBucketResult.allowed();
        }
        long allowed = result.get(0) != null ? ((Number) result.get(0)).longValue() : 0L;
        long retryAfter = result.get(1) != null ? ((Number) result.get(1)).longValue() : 1L;
        if (allowed == 1L) {
            return TokenBucketResult.allowed();
        }
        return TokenBucketResult.rejected((int) retryAfter);
    }
}
