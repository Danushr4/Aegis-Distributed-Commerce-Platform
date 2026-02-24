package com.aegis.orderservice.dto;

import com.aegis.orderservice.Entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {

    private UUID orderId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private Instant createdAt;
}
