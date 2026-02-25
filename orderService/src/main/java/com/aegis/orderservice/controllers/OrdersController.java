package com.aegis.orderservice.controllers;

import com.aegis.orderservice.dto.CreateOrderRequest;
import com.aegis.orderservice.dto.CreateOrderResponse;
import com.aegis.orderservice.dto.IdempotentCreateResult;
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

@RestController
@RequestMapping("/api/v1/orders")
public class OrdersController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IOrderService ordersService;
    private final ObjectMapper objectMapper;

    public OrdersController(IOrderService ordersService, ObjectMapper objectMapper) {
        this.ordersService = ordersService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> listOrders() {
        return ResponseEntity.ok(List.of(
                Map.of("id", "dummy-1", "status", "PENDING", "message", "Dummy response")
        ));
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
