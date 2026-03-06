package com.aegis.orderservice.client;

import java.util.List;
import java.util.UUID;

/**
 * Client for Inventory Service: reserve and release by order ID.
 * Implemented with WebClient, timeouts, retry (transient only), and circuit breaker.
 */
public interface InventoryClient {

    /**
     * Reserve items for an order. Idempotent on inventory side for same orderId.
     *
     * @param orderId order ID
     * @param items   list of (sku, quantity) to reserve
     * @throws ClientErrorException on 4xx (e.g. insufficient stock, SKU not found)
     * @throws RuntimeException     on timeout, 5xx, or circuit open
     */
    void reserve(UUID orderId, List<SkuQty> items);

    /**
     * Release all reservations for an order.
     *
     * @param orderId order ID
     */
    void release(UUID orderId);

    record SkuQty(String sku, int qty) {}
}
