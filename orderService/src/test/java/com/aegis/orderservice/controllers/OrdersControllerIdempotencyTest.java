package com.aegis.orderservice.controllers;

import com.aegis.orderservice.Entity.OrderStatus;
import com.aegis.orderservice.dto.CreateOrderRequest;
import com.aegis.orderservice.dto.CreateOrderResponse;
import com.aegis.orderservice.dto.IdempotentCreateResult;
import com.aegis.orderservice.dto.OrderItemResponse;
import com.aegis.orderservice.dto.OrderResponse;
import com.aegis.orderservice.dto.PageResponse;
import com.aegis.orderservice.services.resources.IOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for idempotency contract: missing key → 400, key present → service called.
 */
@WebMvcTest(OrdersController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(OrdersControllerIdempotencyTest.TestConfig.class)
class OrdersControllerIdempotencyTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        IOrderService orderService() {
            return mock(IOrderService.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    IOrderService orderService;

    @BeforeEach
    void resetMocks() {
        reset(orderService);
    }

    @Test
    void createOrder_withoutIdempotencyKey_returns400() throws Exception {
        String body = """
                {"userId":"u1","currency":"USD","items":[{"sku":"A","qty":1,"unitPrice":10.00}]}
                """;
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Idempotency-Key")));
        verify(orderService, never()).createOrderIdempotent(any(), any());
    }

    @Test
    void createOrder_withBlankIdempotencyKey_returns400() throws Exception {
        String body = """
                {"userId":"u1","currency":"USD","items":[{"sku":"A","qty":1,"unitPrice":10.00}]}
                """;
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verify(orderService, never()).createOrderIdempotent(any(), any());
    }

    @Test
    void createOrder_withIdempotencyKey_callsService() throws Exception {
        String key = "idem-" + UUID.randomUUID();
        String body = """
                {"userId":"u1","currency":"USD","items":[{"sku":"A","qty":1,"unitPrice":10.00}]}
                """;
        UUID orderId = UUID.randomUUID();
        CreateOrderResponse response = CreateOrderResponse.builder()
                .orderId(orderId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("10.00"))
                .createdAt(Instant.now())
                .build();
        String responseJson = "{\"orderId\":\"" + orderId + "\",\"status\":\"PENDING\",\"totalAmount\":10.00,\"createdAt\":\"" + Instant.now() + "\"}";
        when(orderService.createOrderIdempotent(eq(key), any(CreateOrderRequest.class)))
                .thenReturn(IdempotentCreateResult.created(response, responseJson));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
        verify(orderService).createOrderIdempotent(eq(key), any(CreateOrderRequest.class));
    }

    @Test
    void getOrderById_whenFound_returns200WithBody() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse response = OrderResponse.builder()
                .orderId(orderId)
                .userId("u1")
                .currency("USD")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("69.97"))
                .createdAt(Instant.now())
                .items(List.of(
                        OrderItemResponse.builder().id(1L).sku("SKU-1").qty(2).unitPrice(new BigDecimal("19.99")).lineAmount(new BigDecimal("39.98")).build(),
                        OrderItemResponse.builder().id(2L).sku("SKU-2").qty(1).unitPrice(new BigDecimal("29.99")).lineAmount(new BigDecimal("29.99")).build()
                ))
                .build();
        when(orderService.getOrderById(orderId)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.items.length()").value(2));
        verify(orderService).getOrderById(orderId);
    }

    @Test
    void getOrderById_whenNotFound_returns404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrderById(orderId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isNotFound());
        verify(orderService).getOrderById(orderId);
    }

    @Test
    void listOrders_returnsPageWithDefaults() throws Exception {
        PageResponse<OrderResponse> page = PageResponse.<OrderResponse>builder()
                .content(List.of())
                .totalElements(0)
                .totalPages(0)
                .number(0)
                .size(20)
                .first(true)
                .last(true)
                .build();
        when(orderService.getOrders(nullable(String.class), nullable(OrderStatus.class), eq(0), eq(20), eq("createdAt"), eq(true)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20));
        verify(orderService).getOrders(nullable(String.class), nullable(OrderStatus.class), eq(0), eq(20), eq("createdAt"), eq(true));
    }
}
