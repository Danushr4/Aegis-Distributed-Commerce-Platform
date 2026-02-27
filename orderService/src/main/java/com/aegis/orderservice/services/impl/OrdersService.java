package com.aegis.orderservice.services.impl;

import com.aegis.orderservice.Entity.IdempotencyKeys;
import com.aegis.orderservice.Entity.OrderItems;
import com.aegis.orderservice.Entity.OrderStatus;
import com.aegis.orderservice.Entity.Orders;
import com.aegis.orderservice.dto.CreateOrderRequest;
import com.aegis.orderservice.dto.CreateOrderResponse;
import com.aegis.orderservice.dto.IdempotentCreateResult;
import com.aegis.orderservice.dto.OrderItemRequest;
import com.aegis.orderservice.dto.OrderItemResponse;
import com.aegis.orderservice.dto.OrderResponse;
import com.aegis.orderservice.dto.PageResponse;
import com.aegis.orderservice.exception.IdempotencyConflictException;
import com.aegis.orderservice.exception.IdempotencyStillProcessingException;
import com.aegis.orderservice.repository.IdempotencyKeysRepository;
import com.aegis.orderservice.repository.OrderRepository;
import com.aegis.orderservice.metrics.OrderMetrics;
import com.aegis.orderservice.repository.OrderSpecs;
import com.aegis.orderservice.services.OrderCacheService;
import com.aegis.orderservice.services.resources.IOrderService;

import org.springframework.beans.factory.annotation.Autowired;
import com.aegis.orderservice.util.RequestHashUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OrdersService implements IOrderService {

    private static final Logger log = LoggerFactory.getLogger(OrdersService.class);

    private final OrderRepository orderRepository;
    private final IdempotencyKeysRepository idempotencyKeysRepository;
    private final ObjectMapper objectMapper;
    private final Optional<OrderCacheService> orderCache;
    private final Optional<OrderMetrics> orderMetrics;

    public OrdersService(OrderRepository orderRepository,
                         IdempotencyKeysRepository idempotencyKeysRepository,
                         ObjectMapper objectMapper,
                         @Autowired(required = false) OrderCacheService orderCache,
                         @Autowired(required = false) OrderMetrics orderMetrics) {
        this.orderRepository = orderRepository;
        this.idempotencyKeysRepository = idempotencyKeysRepository;
        this.objectMapper = objectMapper;
        this.orderCache = Optional.ofNullable(orderCache);
        this.orderMetrics = Optional.ofNullable(orderMetrics);
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

        orderMetrics.ifPresent(OrderMetrics::recordOrderCreated);

        // after save
        orderCache.ifPresent(cache -> {
            try { cache.invalidate(saved.getId()); }
            catch (Exception e) { log.warn("Failed to invalidate cache after create: {}", saved.getId(), e); }
        });

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
                orderMetrics.ifPresent(OrderMetrics::recordIdempotencyConflict);
                throw new IdempotencyConflictException(
                        "Idempotency key was used for a different request");
            }
            if (IdempotencyKeys.STATUS_COMPLETED.equals(existing.getStatus())) {
                orderMetrics.ifPresent(OrderMetrics::recordIdempotencyHit);
                return IdempotentCreateResult.replay(
                        existing.getResponseCode(),
                        existing.getResponseBody() != null ? existing.getResponseBody() : "{}");
            }
            if (IdempotencyKeys.STATUS_IN_PROGRESS.equals(existing.getStatus())) {
                throw new IdempotencyStillProcessingException();
            }
            orderMetrics.ifPresent(OrderMetrics::recordIdempotencyConflict);
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

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderResponse> getOrderById(UUID orderId) {
        if (orderCache.isEmpty()) {
            return orderRepository.findByIdWithItems(orderId).map(this::toOrderResponse);
        }
        OrderCacheService cache = orderCache.get();
        try {
            Optional<OrderResponse> cached = cache.get(orderId);
            if (cached.isPresent()) return cached;

            boolean gotLock = cache.tryLock(orderId);
            if (gotLock) {
                try {
                    // Double-check cache (another thread may have populated)
                    cached = cache.get(orderId);
                    if (cached.isPresent()) return cached;
                    Optional<OrderResponse> fromDb = orderRepository.findByIdWithItems(orderId).map(this::toOrderResponse);
                    fromDb.ifPresent(r -> cache.set(orderId, r));
                    return fromDb;
                } finally {
                    cache.releaseLock(orderId);
                }
            }
            // Didn't get lock: wait briefly then retry cache or fallback to DB
            Thread.sleep(cache.getWaitAfterLockMissMs());
            cached = cache.get(orderId);
            if (cached.isPresent()) return cached;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for cache", e);
        } catch (Exception e) {
            log.debug("Cache miss or error, falling back to DB: orderId={}", orderId, e);
        }
        return orderRepository.findByIdWithItems(orderId).map(this::toOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrders(String userId, OrderStatus status, int page, int size, String sortProperty, boolean sortDesc) {
        Sort sort = sortBy(sortProperty, sortDesc);
        Pageable pageable = PageRequest.of(page, size, sort);
        Specification<Orders> spec = OrderSpecs.withUserId(userId).and(OrderSpecs.withStatus(status));
        var springPage = orderRepository.findAll(spec, pageable);
        List<OrderResponse> content = springPage.getContent().stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());
        return PageResponse.<OrderResponse>builder()
                .content(content)
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .number(springPage.getNumber())
                .size(springPage.getSize())
                .first(springPage.isFirst())
                .last(springPage.isLast())
                .build();
    }

    private OrderResponse toOrderResponse(Orders o) {
        List<OrderItemResponse> items = o.getItems() == null ? List.of() : o.getItems().stream()
                .map(this::toOrderItemResponse)
                .collect(Collectors.toList());
        return OrderResponse.builder()
                .orderId(o.getId())
                .userId(o.getUserId())
                .currency(o.getCurrency())
                .status(o.getStatus())
                .totalAmount(o.getTotalAmount())
                .createdAt(o.getCreatedAt())
                .items(items)
                .build();
    }

    private OrderItemResponse toOrderItemResponse(OrderItems i) {
        return OrderItemResponse.builder()
                .id(i.getId())
                .sku(i.getSku())
                .qty(i.getQty())
                .unitPrice(i.getUnitPrice())
                .lineAmount(i.getLineAmount())
                .build();
    }

    private static Sort sortBy(String sortProperty, boolean sortDesc) {
        String property = (sortProperty == null || sortProperty.isBlank()) ? "createdAt" : sortProperty.trim();
        Sort.Direction direction = sortDesc ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
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
