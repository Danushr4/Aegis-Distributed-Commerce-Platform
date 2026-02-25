package com.aegis.orderservice.util;

import com.aegis.orderservice.dto.CreateOrderRequest;
import com.aegis.orderservice.dto.OrderItemRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * Canonical request hash (SHA-256) for idempotency: userId, currency, items sorted by sku (qty, unitPrice).
 */
public final class RequestHashUtil {

    private RequestHashUtil() {}

    public static String computeRequestHash(CreateOrderRequest request) {
        String canonical = buildCanonicalRequest(request);
        return sha256Hex(canonical);
    }

    static String buildCanonicalRequest(CreateOrderRequest request) {
        // Items sorted by sku for deterministic order
        String itemsPart = request.getItems().stream()
                .sorted(Comparator.comparing(OrderItemRequest::getSku))
                .map(RequestHashUtil::itemLine)
                .collect(Collectors.joining("|"));
        return "userId=" + request.getUserId()
                + ";currency=" + request.getCurrency()
                + ";items=" + itemsPart;
    }

    private static String itemLine(OrderItemRequest item) {
        return item.getSku() + "," + item.getQty() + "," + toPlainString(item.getUnitPrice());
    }

    private static String toPlainString(BigDecimal bd) {
        return bd.stripTrailingZeros().toPlainString();
    }

    static String sha256Hex(String input) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
