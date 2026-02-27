package com.aegis.orderservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Custom metrics for orders and idempotency. Use for dashboards and SLOs.
 */
@Component
public class OrderMetrics {

    private static final String ORDERS_CREATED = "orders.created.count";
    private static final String ORDERS_CREATE_LATENCY = "orders.create.latency";
    private static final String IDEMPOTENCY_HIT = "idempotency.hit.count";
    private static final String IDEMPOTENCY_CONFLICT = "idempotency.conflict.count";
    private static final String CACHE_HIT = "cache.hit.count";
    private static final String CACHE_MISS = "cache.miss.count";

    private final Counter ordersCreatedCount;
    private final Timer ordersCreateLatency;
    private final Counter idempotencyHitCount;
    private final Counter idempotencyConflictCount;
    private final Counter cacheHitCount;
    private final Counter cacheMissCount;

    public OrderMetrics(MeterRegistry registry) {
        this.ordersCreatedCount = registry.counter(ORDERS_CREATED);
        this.ordersCreateLatency = registry.timer(ORDERS_CREATE_LATENCY);
        this.idempotencyHitCount = registry.counter(IDEMPOTENCY_HIT);
        this.idempotencyConflictCount = registry.counter(IDEMPOTENCY_CONFLICT);
        this.cacheHitCount = registry.counter(CACHE_HIT);
        this.cacheMissCount = registry.counter(CACHE_MISS);
    }

    public void recordOrderCreated() {
        ordersCreatedCount.increment();
    }

    public Timer.Sample startCreateLatency() {
        return Timer.start();
    }

    public void recordCreateLatency(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(ordersCreateLatency);
        }
    }

    public void recordIdempotencyHit() {
        idempotencyHitCount.increment();
    }

    public void recordIdempotencyConflict() {
        idempotencyConflictCount.increment();
    }

    public void recordCacheHit() {
        cacheHitCount.increment();
    }

    public void recordCacheMiss() {
        cacheMissCount.increment();
    }
}
