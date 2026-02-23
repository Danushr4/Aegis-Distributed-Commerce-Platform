package com.aegis.orderservice.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrdersController {

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> listOrders() {
        return ResponseEntity.ok(List.of(
                Map.of("id", "dummy-1", "status", "PENDING", "message", "Dummy response")
        ));
    }
}
