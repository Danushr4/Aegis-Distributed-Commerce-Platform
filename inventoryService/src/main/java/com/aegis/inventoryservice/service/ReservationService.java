package com.aegis.inventoryservice.service;

import com.aegis.inventoryservice.dto.ReserveRequest;
import com.aegis.inventoryservice.dto.ReservationResponse;
import com.aegis.inventoryservice.entity.Inventory;
import com.aegis.inventoryservice.entity.Reservation;
import com.aegis.inventoryservice.exception.InsufficientStockException;
import com.aegis.inventoryservice.exception.SkuNotFoundException;
import com.aegis.inventoryservice.repository.InventoryRepository;
import com.aegis.inventoryservice.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reserve items for an order (idempotent by orderId). Release restores stock.
 * Prevents negative stock by locking inventory rows during reserve.
 */
@Service
public class ReservationService {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;

    public ReservationService(InventoryRepository inventoryRepository,
                              ReservationRepository reservationRepository) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
    }

    /**
     * Reserve items for an order. Idempotent: if reservations already exist for this orderId,
     * returns them without deducting stock again.
     */
    @Transactional
    public ReservationResponse reserve(ReserveRequest request) {
        UUID orderId = request.orderId();

        if (reservationRepository.existsByOrderId(orderId)) {
            List<Reservation> existing = reservationRepository.findByOrderId(orderId);
            return toResponse(orderId, existing);
        }

        Instant now = Instant.now();
        List<Reservation> created = new ArrayList<>();

        for (ReserveRequest.ReserveItem item : request.items()) {
            String sku = item.sku();
            int qty = item.qty();

            Inventory inv = inventoryRepository.findBySkuForUpdate(sku)
                    .orElseThrow(() -> new SkuNotFoundException("SKU not found: " + sku));

            int available = inv.getAvailableQty();
            if (available < qty) {
                throw new InsufficientStockException(
                        "Insufficient stock for SKU " + sku + ": available=" + available + ", requested=" + qty);
            }

            inv.setAvailableQty(available - qty);
            inventoryRepository.save(inv);

            Reservation r = new Reservation();
            r.setOrderId(orderId);
            r.setSku(sku);
            r.setQty(qty);
            r.setCreatedAt(now);
            created.add(reservationRepository.save(r));
        }

        return toResponse(orderId, created);
    }

    /**
     * Release all reservations for an order; restores available_qty.
     */
    @Transactional
    public void release(UUID orderId) {
        List<Reservation> list = reservationRepository.findByOrderId(orderId);
        for (Reservation r : list) {
            inventoryRepository.findById(r.getSku()).ifPresent(inv -> {
                inv.setAvailableQty(inv.getAvailableQty() + r.getQty());
                inventoryRepository.save(inv);
            });
        }
        reservationRepository.deleteAll(list);
    }

    public ReservationResponse getByOrderId(UUID orderId) {
        List<Reservation> list = reservationRepository.findByOrderId(orderId);
        if (list.isEmpty()) {
            return null;
        }
        return toResponse(orderId, list);
    }

    public Integer getAvailableQty(String sku) {
        return inventoryRepository.findById(sku)
                .map(Inventory::getAvailableQty)
                .orElse(null);
    }

    private static ReservationResponse toResponse(UUID orderId, List<Reservation> list) {
        Instant createdAt = list.isEmpty() ? Instant.now() : list.get(0).getCreatedAt();
        List<ReservationResponse.ReservationItemResponse> items = list.stream()
                .map(r -> new ReservationResponse.ReservationItemResponse(r.getSku(), r.getQty()))
                .toList();
        return new ReservationResponse(orderId, items, createdAt);
    }
}
