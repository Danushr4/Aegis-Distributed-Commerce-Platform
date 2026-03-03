package com.aegis.inventoryservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReserveRequest(
        @NotNull(message = "orderId is required") UUID orderId,
        @NotEmpty(message = "items is required") @Valid List<ReserveItem> items
) {
    public record ReserveItem(
            @NotNull(message = "sku is required") String sku,
            @Min(value = 1, message = "qty must be positive") int qty
    ) {}
}
