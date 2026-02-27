package com.aegis.orderservice.client;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Dummy dependency client protected by retry (transient only), circuit breaker, and bulkhead.
 * Calls a configurable dummy endpoint; replace with real Inventory/Payment calls later.
 */
@Component
public class DummyDependencyClient {

    private static final Logger log = LoggerFactory.getLogger(DummyDependencyClient.class);
    private static final String INSTANCE = "dummyDependency";

    private final WebClient webClient;
    private final String dummyBaseUrl;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;

    public DummyDependencyClient(WebClient webClient,
                                 @Value("${app.dependency.dummyBaseUrl:http://localhost:9999}") String dummyBaseUrl,
                                 CircuitBreakerRegistry circuitBreakerRegistry,
                                 RetryRegistry retryRegistry,
                                 BulkheadRegistry bulkheadRegistry) {
        this.webClient = webClient;
        this.dummyBaseUrl = dummyBaseUrl.endsWith("/") ? dummyBaseUrl : dummyBaseUrl + "/";
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCE);
        this.retry = retryRegistry.retry(INSTANCE);
        this.bulkhead = bulkheadRegistry.bulkhead(INSTANCE);
    }

    /**
     * Performs a single GET to the dummy dependency. Protected by bulkhead → retry → circuit breaker.
     * Retries only on transient failures (timeouts, 5xx). Circuit opens after threshold; bulkhead limits concurrency.
     *
     * @return response body or default message; throws on non-transient failure or when circuit is open
     */
    public String call() {
        return Bulkhead.decorateSupplier(bulkhead, () ->
                Retry.decorateSupplier(retry, () ->
                        CircuitBreaker.decorateSupplier(circuitBreaker, this::doCall).get()
                ).get()
        ).get();
    }

    private String doCall() {
        String url = dummyBaseUrl + "dummy";
        try {
            return webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status >= 500) {
                log.warn("Dummy dependency returned 5xx: {} {}", status, url);
                throw e; // retried by Resilience4j
            }
            throw new ClientErrorException("Dependency returned " + status, e); // not retried
        }
    }
}
