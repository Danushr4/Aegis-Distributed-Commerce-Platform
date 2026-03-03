package com.aegis.inventoryservice.repository;

import com.aegis.inventoryservice.entity.Reservation;
import com.aegis.inventoryservice.entity.ReservationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, ReservationId> {

    List<Reservation> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);
}
