package com.aegis.inventoryservice.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ReservationId implements Serializable {

    private UUID orderId;
    private String sku;

    public ReservationId() {}

    public ReservationId(UUID orderId, String sku) {
        this.orderId = orderId;
        this.sku = sku;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReservationId that = (ReservationId) o;
        return Objects.equals(orderId, that.orderId) && Objects.equals(sku, that.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, sku);
    }
}
