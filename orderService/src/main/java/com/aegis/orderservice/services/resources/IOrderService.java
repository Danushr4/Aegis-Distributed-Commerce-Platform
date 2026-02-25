package com.aegis.orderservice.services.resources;

import com.aegis.orderservice.dto.CreateOrderRequest;
import com.aegis.orderservice.dto.CreateOrderResponse;
import com.aegis.orderservice.dto.IdempotentCreateResult;

public interface IOrderService {

    CreateOrderResponse createOrder(CreateOrderRequest request);

    /**
     * Idempotent create: same key + same request → same response; same key + different request → 409.
     */
    IdempotentCreateResult createOrderIdempotent(String idempotencyKey, CreateOrderRequest request);

}