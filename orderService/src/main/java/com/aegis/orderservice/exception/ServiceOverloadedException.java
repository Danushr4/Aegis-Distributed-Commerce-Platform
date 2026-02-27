package com.aegis.orderservice.exception;

/**
 * Thrown when the service cannot accept more work (e.g. backpressure semaphore saturated).
 * Mapped to 503 Service Unavailable.
 */
public class ServiceOverloadedException extends RuntimeException {

    public ServiceOverloadedException(String message) {
        super(message);
    }
}
