package com.aegis.orderservice.services.resources;

import com.aegis.orderservice.dto.CreateOrderRequest;
import com.aegis.orderservice.dto.CreateOrderResponse;

public interface IOrderService {

    CreateOrderResponse createOrder(CreateOrderRequest request);

}