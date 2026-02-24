package com.aegis.orderservice.services.impl;

import com.aegis.orderservice.Entity.OrderItems;
import com.aegis.orderservice.Entity.OrderStatus;
import com.aegis.orderservice.Entity.Orders;
import com.aegis.orderservice.dto.CreateOrderRequest;
import com.aegis.orderservice.dto.CreateOrderResponse;
import com.aegis.orderservice.dto.OrderItemRequest;
import com.aegis.orderservice.repository.OrderRepository;
import com.aegis.orderservice.services.resources.IOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrdersService implements IOrderService {

    private final OrderRepository orderRepository;

    public OrdersService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        Instant now = Instant.now();
        Orders order = new Orders();
        order.setUserId(request.getUserId());
        order.setCurrency(request.getCurrency());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItems> items = new ArrayList<>();

        for (OrderItemRequest itemReq : request.getItems()) {
            BigDecimal lineAmount = itemReq.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQty()));
            totalAmount = totalAmount.add(lineAmount);

            OrderItems item = new OrderItems();
            item.setOrder(order);
            item.setSku(itemReq.getSku());
            item.setQty(itemReq.getQty());
            item.setUnitPrice(itemReq.getUnitPrice());
            item.setLineAmount(lineAmount);
            items.add(item);
        }

        order.setTotalAmount(totalAmount);
        order.setItems(items);

        Orders saved = orderRepository.save(order);

        return CreateOrderResponse.builder()
                .orderId(saved.getId())
                .status(saved.getStatus())
                .totalAmount(saved.getTotalAmount())
                .createdAt(saved.getCreatedAt())
                .build();
    }
}
