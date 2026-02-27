package com.aegis.orderservice.client;

import java.math.BigDecimal;

/**
 * Future client for Payment Service. Stub for now; will call authorize/capture when service exists.
 */
public interface PaymentClient {

    /**
     * Authorize payment for an order (to be implemented when Payment Service is available).
     *
     * @param orderId   order id
     * @param amount    amount to authorize
     * @param currency  currency code
     * @return payment authorization id or throw on failure
     */
    String authorize(String orderId, BigDecimal amount, String currency);

    /**
     * Capture a previously authorized payment (to be implemented when Payment Service is available).
     */
    void capture(String authorizationId);
}
