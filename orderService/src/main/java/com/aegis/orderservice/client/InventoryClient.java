package com.aegis.orderservice.client;

import java.util.List;

/**
 * Future client for Inventory Service. Stub for now; will call reserve/release when service exists.
 */
public interface InventoryClient {

    /**
     * Reserve items for order (to be implemented when Inventory Service is available).
     *
     * @param skuQuantities list of (sku, quantity) to reserve
     * @return reservation id or throw on failure
     */
    String reserve(List<SkuQty> skuQuantities);

    /**
     * Release a reservation (to be implemented when Inventory Service is available).
     */
    void release(String reservationId);

    record SkuQty(String sku, int qty) {}
}
