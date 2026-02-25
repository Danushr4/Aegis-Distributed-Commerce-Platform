package com.aegis.orderservice.exception;

/**
 * Same idempotency key was used for a different request (client bug).
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
