package com.aegis.orderservice.dto;

import com.aegis.orderservice.Entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full order response: same shape as create response (orderId, status, totalAmount, createdAt) plus items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID orderId;
    private String userId;
    private String currency;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private Instant createdAt;
    private List<OrderItemResponse> items;
}
