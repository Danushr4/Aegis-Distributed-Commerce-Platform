package com.aegis.orderservice.client;

import com.aegis.orderservice.client.dto.AuthorizeRequestDto;
import com.aegis.orderservice.client.dto.AuthorizeResponseDto;
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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment Service client: authorize with WebClient, timeouts, retry (transient only), circuit breaker.
 */
@Component
@ConditionalOnProperty(prefix = "app.dependency", name = "paymentEnabled", havingValue = "true", matchIfMissing = true)
public class WebClientPaymentClient implements PaymentClient {

	private static final Logger log = LoggerFactory.getLogger(WebClientPaymentClient.class);
	private static final String INSTANCE = "payment";

	private final WebClient webClient;
	private final String baseUrl;
	private final CircuitBreaker circuitBreaker;
	private final Retry retry;
	private final Bulkhead bulkhead;

	public WebClientPaymentClient(WebClient webClient,
	                              @Value("${app.dependency.paymentBaseUrl:http://localhost:8082}") String paymentBaseUrl,
	                              CircuitBreakerRegistry circuitBreakerRegistry,
	                              RetryRegistry retryRegistry,
	                              BulkheadRegistry bulkheadRegistry) {
		this.webClient = webClient;
		this.baseUrl = paymentBaseUrl.endsWith("/") ? paymentBaseUrl : paymentBaseUrl + "/";
		this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCE);
		this.retry = retryRegistry.retry(INSTANCE);
		this.bulkhead = bulkheadRegistry.bulkhead(INSTANCE);
	}

	@Override
	public UUID authorize(UUID orderId, BigDecimal amount, String currency) {
		return Bulkhead.decorateSupplier(bulkhead, () ->
				Retry.decorateSupplier(retry, () ->
						CircuitBreaker.decorateSupplier(circuitBreaker, () -> doAuthorize(orderId, amount, currency)).get()
				).get()
		).get();
	}

	private UUID doAuthorize(UUID orderId, BigDecimal amount, String currency) {
		String url = baseUrl + "api/v1/payments/authorize";
		AuthorizeRequestDto body = new AuthorizeRequestDto(orderId, amount, currency);
		try {
			AuthorizeResponseDto response = webClient.post()
					.uri(url)
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(body)
					.retrieve()
					.bodyToMono(AuthorizeResponseDto.class)
					.block();
			if (response == null || response.authorizationId() == null) {
				throw new IllegalStateException("Payment authorize returned no authorizationId");
			}
			return response.authorizationId();
		} catch (WebClientResponseException e) {
			int status = e.getStatusCode().value();
			if (status >= 500) {
				log.warn("Payment authorize returned 5xx: {} {}", status, url);
				throw e;
			}
			throw new ClientErrorException("Payment returned " + status + ": " + e.getResponseBodyAsString(), e);
		}
	}
}
