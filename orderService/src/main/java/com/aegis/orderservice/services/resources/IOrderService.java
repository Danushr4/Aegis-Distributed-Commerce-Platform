package com.aegis.orderservice.services.resources;

import com.aegis.orderservice.Entity.OrderStatus;
import com.aegis.orderservice.dto.CreateOrderRequest;
import com.aegis.orderservice.dto.CreateOrderResponse;
import com.aegis.orderservice.dto.IdempotentCreateResult;
import com.aegis.orderservice.dto.OrderResponse;
import com.aegis.orderservice.dto.PageResponse;

import java.util.Optional;
import java.util.UUID;

public interface IOrderService {

    CreateOrderResponse createOrder(CreateOrderRequest request);

    /**
     * Idempotent create: same key + same request → same response; same key + different request → 409.
     */
    IdempotentCreateResult createOrderIdempotent(String idempotencyKey, CreateOrderRequest request);

    /**
     * Get single order by id with items. Returns empty if not found.
     */
    Optional<OrderResponse> getOrderById(UUID orderId);

    /**
     * List orders with optional userId/status filter, pagination, and deterministic sort (default createdAt,desc).
     */
    PageResponse<OrderResponse> getOrders(String userId, OrderStatus status, int page, int size, String sortProperty, boolean sortDesc);
}