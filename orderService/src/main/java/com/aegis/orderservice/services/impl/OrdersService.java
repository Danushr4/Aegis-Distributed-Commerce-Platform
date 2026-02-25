package com.aegis.orderservice.services.impl;

import com.aegis.orderservice.Entity.IdempotencyKeys;
import com.aegis.orderservice.Entity.OrderItems;
import com.aegis.orderservice.Entity.OrderStatus;
import com.aegis.orderservice.Entity.Orders;
import com.aegis.orderservice.dto.CreateOrderRequest;
import com.aegis.orderservice.dto.CreateOrderResponse;
import com.aegis.orderservice.dto.IdempotentCreateResult;
import com.aegis.orderservice.dto.OrderItemRequest;
import com.aegis.orderservice.exception.IdempotencyConflictException;
import com.aegis.orderservice.exception.IdempotencyStillProcessingException;
import com.aegis.orderservice.repository.IdempotencyKeysRepository;
import com.aegis.orderservice.repository.OrderRepository;
import com.aegis.orderservice.services.resources.IOrderService;
import com.aegis.orderservice.util.RequestHashUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrdersService implements IOrderService {

    private final OrderRepository orderRepository;
    private final IdempotencyKeysRepository idempotencyKeysRepository;
    private final ObjectMapper objectMapper;

    public OrdersService(OrderRepository orderRepository,
                         IdempotencyKeysRepository idempotencyKeysRepository,
                         ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.idempotencyKeysRepository = idempotencyKeysRepository;
        this.objectMapper = objectMapper;
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

    @Override
    @Transactional
    public IdempotentCreateResult createOrderIdempotent(String idempotencyKey, CreateOrderRequest request) {
        String requestHash = RequestHashUtil.computeRequestHash(request);
        Instant now = Instant.now();

        IdempotencyKeys newRow = new IdempotencyKeys();
        newRow.setKey(idempotencyKey);
        newRow.setRequestHash(requestHash);
        newRow.setStatus(IdempotencyKeys.STATUS_IN_PROGRESS);
        newRow.setCreatedAt(now);
        newRow.setUpdatedAt(now);

        try {
            idempotencyKeysRepository.saveAndFlush(newRow);
        } catch (DataIntegrityViolationException e) {
            // Duplicate key: fetch existing and decide
            IdempotencyKeys existing = idempotencyKeysRepository.findById(idempotencyKey).orElseThrow();
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                        "Idempotency key was used for a different request");
            }
            if (IdempotencyKeys.STATUS_COMPLETED.equals(existing.getStatus())) {
                return IdempotentCreateResult.replay(
                        existing.getResponseCode(),
                        existing.getResponseBody() != null ? existing.getResponseBody() : "{}");
            }
            if (IdempotencyKeys.STATUS_IN_PROGRESS.equals(existing.getStatus())) {
                throw new IdempotencyStillProcessingException();
            }
            throw new IdempotencyConflictException("Invalid idempotency status: " + existing.getStatus());
        }

        try {
            CreateOrderResponse response = createOrder(request);
            String responseBodyJson = objectMapper.writeValueAsString(response);

            newRow.setStatus(IdempotencyKeys.STATUS_COMPLETED);
            newRow.setOrderId(response.getOrderId());
            newRow.setResponseCode(201);
            newRow.setResponseBody(responseBodyJson);
            newRow.setUpdatedAt(Instant.now());
            idempotencyKeysRepository.save(newRow);

            return IdempotentCreateResult.created(response, responseBodyJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize response", e);
        } catch (Exception e) {
            // Store failure for replay (same key returns same error)
            String errorBody = "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
            newRow.setStatus(IdempotencyKeys.STATUS_COMPLETED);
            newRow.setResponseCode(500);
            newRow.setResponseBody(errorBody);
            newRow.setUpdatedAt(Instant.now());
            idempotencyKeysRepository.save(newRow);
            throw e;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
