package com.aegis.inventoryservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID orderId,
        List<ReservationItemResponse> items,
        Instant createdAt
) {
    public record ReservationItemResponse(String sku, int qty) {}
}
