package com.aegis.orderservice.repository;

import com.aegis.orderservice.Entity.OrderStatus;
import com.aegis.orderservice.Entity.Orders;
import org.springframework.data.jpa.domain.Specification;

public final class OrderSpecs {

    private OrderSpecs() {}

    public static Specification<Orders> withUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("userId"), userId.trim());
    }

    public static Specification<Orders> withStatus(OrderStatus status) {
        if (status == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
