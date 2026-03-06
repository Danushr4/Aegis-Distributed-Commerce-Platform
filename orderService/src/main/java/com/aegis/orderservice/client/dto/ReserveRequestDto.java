package com.aegis.orderservice.client.dto;

import com.aegis.orderservice.client.InventoryClient;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * Request body for Inventory Service POST /api/v1/reservations.
 */
public record ReserveRequestDto(
        @JsonProperty("orderId") UUID orderId,
        @JsonProperty("items") List<ReserveItemDto> items
) {
    public static ReserveRequestDto from(UUID orderId, List<InventoryClient.SkuQty> skuQuantities) {
        List<ReserveItemDto> items = skuQuantities.stream()
                .map(sq -> new ReserveItemDto(sq.sku(), sq.qty()))
                .toList();
        return new ReserveRequestDto(orderId, items);
    }

    public record ReserveItemDto(
            @JsonProperty("sku") String sku,
            @JsonProperty("qty") int qty
    ) {}
}
