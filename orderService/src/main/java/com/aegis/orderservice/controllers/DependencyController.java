package com.aegis.orderservice.controllers;

import com.aegis.orderservice.client.DummyDependencyClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint that calls the dummy dependency through the resilience-protected client.
 * Use for demos; replace with real health/readiness checks to Inventory/Payment later.
 */
@RestController
@RequestMapping("/api/v1")
public class DependencyController {

    private final DummyDependencyClient dummyDependencyClient;

    public DependencyController(DummyDependencyClient dummyDependencyClient) {
        this.dummyDependencyClient = dummyDependencyClient;
    }

    @GetMapping("/dependency-status")
    public ResponseEntity<?> dependencyStatus() {
        try {
            String result = dummyDependencyClient.call();
            return ResponseEntity.ok(Map.of("status", "ok", "dependencyResponse", result != null ? result : ""));
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(Map.of("status", "dependency_unavailable", "error", e.getMessage()));
        }
    }
}
