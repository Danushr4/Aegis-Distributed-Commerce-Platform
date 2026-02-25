package com.aegis.orderservice.exception;

/**
 * Request with this idempotency key is still processing; client should retry later.
 */
public class IdempotencyStillProcessingException extends RuntimeException {

    public IdempotencyStillProcessingException() {
        super("Request with this idempotency key is still processing, retry later.");
    }
}
