package com.aegis.inventoryservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reservations")
@IdClass(ReservationId.class)
public class Reservation {

    @Id
    @Column(name = "order_id", nullable = false)
    private java.util.UUID orderId;

    @Id
    @Column(name = "sku", nullable = false, length = 255)
    private String sku;

    @Column(name = "qty", nullable = false)
    private int qty;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public java.util.UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(java.util.UUID orderId) {
        this.orderId = orderId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
