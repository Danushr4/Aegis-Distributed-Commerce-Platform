package com.aegis.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Semaphore;

/**
 * Backpressure: bounded concurrency for order creation. When saturated, fail fast with 503.
 */
@Configuration
public class BackpressureConfig {

    @Bean
    public Semaphore orderCreateSemaphore(
            @Value("${app.backpressure.max-concurrent-order-creates:50}") int maxConcurrent) {
        return new Semaphore(maxConcurrent);
    }
}
