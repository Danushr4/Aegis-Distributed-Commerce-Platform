package com.aegis.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Result of an idempotent create-order: either a new creation (201) or a replay of stored response.
 */
@Getter
@AllArgsConstructor
public class IdempotentCreateResult {

    public enum Type { CREATED, REPLAY }

    private final Type type;
    private final int responseCode;
    private final String responseBodyJson;
    private final CreateOrderResponse response;

    public static IdempotentCreateResult created(CreateOrderResponse response, String responseBodyJson) {
        return new IdempotentCreateResult(Type.CREATED, 201, responseBodyJson, response);
    }

    public static IdempotentCreateResult replay(int responseCode, String responseBodyJson) {
        return new IdempotentCreateResult(Type.REPLAY, responseCode, responseBodyJson, null);
    }
}
