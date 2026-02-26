package com.aegis.orderservice.repository;

import com.aegis.orderservice.Entity.Orders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Orders, UUID>, JpaSpecificationExecutor<Orders> {

    @Query("SELECT o FROM Orders o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Orders> findByIdWithItems(@Param("id") UUID id);
}
