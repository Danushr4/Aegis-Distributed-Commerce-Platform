package com.aegis.orderservice.client;

import com.aegis.orderservice.client.dto.ReserveRequestDto;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.UUID;

/**
 * Inventory Service client using WebClient with timeouts (from global WebClient),
 * retry (only for transient failures: timeouts, 5xx), and circuit breaker.
 * Fails fast when inventory is down (circuit open) or on 4xx (no retry).
 */
@Component
@ConditionalOnProperty(prefix = "app.dependency", name = "inventoryEnabled", havingValue = "true", matchIfMissing = true)
public class WebClientInventoryClient implements InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(WebClientInventoryClient.class);
    private static final String INSTANCE = "inventory";

    private final WebClient webClient;
    private final String baseUrl;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;

    public WebClientInventoryClient(WebClient webClient,
                                    @Value("${app.dependency.inventoryBaseUrl:http://localhost:8081}") String inventoryBaseUrl,
                                    CircuitBreakerRegistry circuitBreakerRegistry,
                                    RetryRegistry retryRegistry,
                                    BulkheadRegistry bulkheadRegistry) {
        this.webClient = webClient;
        this.baseUrl = inventoryBaseUrl.endsWith("/") ? inventoryBaseUrl : inventoryBaseUrl + "/";
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCE);
        this.retry = retryRegistry.retry(INSTANCE);
        this.bulkhead = bulkheadRegistry.bulkhead(INSTANCE);
    }

    @Override
    public void reserve(UUID orderId, List<SkuQty> items) {
        Bulkhead.decorateRunnable(bulkhead, () ->
                Retry.decorateRunnable(retry, () ->
                        CircuitBreaker.decorateRunnable(circuitBreaker, () -> doReserve(orderId, items)).run()
                ).run()
        ).run();
    }

    private void doReserve(UUID orderId, List<SkuQty> items) {
        String url = baseUrl + "api/v1/reservations";
        ReserveRequestDto body = ReserveRequestDto.from(orderId, items);
        try {
            webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status >= 500) {
                log.warn("Inventory reserve returned 5xx: {} {}", status, url);
                throw e;
            }
            throw new ClientErrorException("Inventory returned " + status + ": " + e.getResponseBodyAsString(), e);
        }
    }

    @Override
    public void release(UUID orderId) {
        Bulkhead.decorateRunnable(bulkhead, () ->
                Retry.decorateRunnable(retry, () ->
                        CircuitBreaker.decorateRunnable(circuitBreaker, () -> doRelease(orderId)).run()
                ).run()
        ).run();
    }

    private void doRelease(UUID orderId) {
        String url = baseUrl + "api/v1/reservations/" + orderId;
        try {
            webClient.delete()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status >= 500) {
                log.warn("Inventory release returned 5xx: {} {}", status, url);
                throw e;
            }
            throw new ClientErrorException("Inventory returned " + status, e);
        }
    }
}
