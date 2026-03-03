package com.aegis.inventoryservice.controller;

import com.aegis.inventoryservice.dto.InventoryResponse;
import com.aegis.inventoryservice.dto.ReserveRequest;
import com.aegis.inventoryservice.dto.ReservationResponse;
import com.aegis.inventoryservice.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReservationsController {

    private final ReservationService reservationService;

    public ReservationsController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping(value = "/reservations", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveRequest request) {
        ReservationResponse response = reservationService.reserve(request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/reservations/{orderId}")
    public ResponseEntity<Void> release(@PathVariable UUID orderId) {
        reservationService.release(orderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/inventory/{sku}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable String sku) {
        Integer available = reservationService.getAvailableQty(sku);
        if (available == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new InventoryResponse(sku, available));
    }
}
