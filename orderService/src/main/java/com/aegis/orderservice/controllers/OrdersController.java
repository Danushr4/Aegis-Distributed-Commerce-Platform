package com.aegis.orderservice.controllers;

import com.aegis.orderservice.Entity.OrderStatus;
import com.aegis.orderservice.dto.CreateOrderRequest;
import com.aegis.orderservice.dto.CreateOrderResponse;
import com.aegis.orderservice.dto.IdempotentCreateResult;
import com.aegis.orderservice.dto.OrderResponse;
import com.aegis.orderservice.dto.PageResponse;
import com.aegis.orderservice.services.resources.IOrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrdersController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_PAGE = 0;
    private static final String DEFAULT_SORT = "createdAt";
    private static final String DEFAULT_SORT_DIR = "desc";

    private final IOrderService ordersService;
    private final ObjectMapper objectMapper;

    public OrdersController(IOrderService ordersService, ObjectMapper objectMapper) {
        this.ordersService = ordersService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrderById(@PathVariable UUID orderId) {
        return ordersService.getOrderById(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PageResponse<OrderResponse>> listOrders(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String sortDir) {
        if (page < 0) page = DEFAULT_PAGE;
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        OrderStatus statusEnum = parseOrderStatus(status);
        boolean sortDesc = !"asc".equalsIgnoreCase(sortDir != null ? sortDir.trim() : DEFAULT_SORT_DIR);
        String sortProperty = (sort == null || sort.isBlank()) ? DEFAULT_SORT : sort.trim();
        PageResponse<OrderResponse> result = ordersService.getOrders(userId, statusEnum, page, size, sortProperty, sortDesc);
        return ResponseEntity.ok(result);
    }

    private static OrderStatus parseOrderStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createOrder(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Idempotency-Key header is required"));
        }
        IdempotentCreateResult result = ordersService.createOrderIdempotent(idempotencyKey.trim(), request);
        if (result.getType() == IdempotentCreateResult.Type.CREATED) {
            CreateOrderResponse created = result.getResponse();
            return ResponseEntity
                    .status(201)
                    .location(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.getOrderId()).toUri())
                    .body(created);
        }
        // REPLAY: return stored status and exact stored body (parse so we don't double-encode)
        try {
            Object body = objectMapper.readValue(result.getResponseBodyJson(), Object.class);
            return ResponseEntity
                    .status(result.getResponseCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored response body is not valid JSON", e);
        }
    }
}
