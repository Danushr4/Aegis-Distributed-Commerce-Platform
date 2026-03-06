package com.aegis.orderservice.client;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Client for Payment Service: authorize (and optionally capture).
 * Implemented with WebClient, timeouts, retry, and circuit breaker.
 */
public interface PaymentClient {

	/**
	 * Authorize payment for an order.
	 *
	 * @param orderId  order ID
	 * @param amount   total amount
	 * @param currency currency code
	 * @return authorization ID on success
	 * @throws ClientErrorException on 4xx (e.g. declined)
	 * @throws RuntimeException     on timeout, 5xx, or circuit open
	 */
	UUID authorize(UUID orderId, BigDecimal amount, String currency);
}
