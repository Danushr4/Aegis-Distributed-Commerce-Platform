package com.aegis.orderservice.util;

import com.aegis.orderservice.dto.CreateOrderRequest;
import com.aegis.orderservice.dto.OrderItemRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHashUtilTest {

    @Test
    void sameRequestProducesSameHash() {
        CreateOrderRequest req = request("u1", "USD", List.of(
                item("SKU-A", 2, "10.00"),
                item("SKU-B", 1, "20.00")
        ));
        String h1 = RequestHashUtil.computeRequestHash(req);
        String h2 = RequestHashUtil.computeRequestHash(req);
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64); // SHA-256 hex
    }

    @Test
    void differentRequestProducesDifferentHash() {
        CreateOrderRequest req1 = request("u1", "USD", List.of(item("SKU-A", 1, "10.00")));
        CreateOrderRequest req2 = request("u2", "USD", List.of(item("SKU-A", 1, "10.00")));
        assertThat(RequestHashUtil.computeRequestHash(req1))
                .isNotEqualTo(RequestHashUtil.computeRequestHash(req2));
    }

    @Test
    void itemOrderDoesNotMatter_canonicalSortBySku() {
        CreateOrderRequest req1 = request("u1", "USD", List.of(
                item("SKU-B", 1, "20.00"),
                item("SKU-A", 2, "10.00")
        ));
        CreateOrderRequest req2 = request("u1", "USD", List.of(
                item("SKU-A", 2, "10.00"),
                item("SKU-B", 1, "20.00")
        ));
        assertThat(RequestHashUtil.computeRequestHash(req1))
                .isEqualTo(RequestHashUtil.computeRequestHash(req2));
    }

    private static CreateOrderRequest request(String userId, String currency, List<OrderItemRequest> items) {
        CreateOrderRequest r = new CreateOrderRequest();
        r.setUserId(userId);
        r.setCurrency(currency);
        r.setItems(items);
        return r;
    }

    private static OrderItemRequest item(String sku, int qty, String unitPrice) {
        OrderItemRequest i = new OrderItemRequest();
        i.setSku(sku);
        i.setQty(qty);
        i.setUnitPrice(new BigDecimal(unitPrice));
        return i;
    }
}
