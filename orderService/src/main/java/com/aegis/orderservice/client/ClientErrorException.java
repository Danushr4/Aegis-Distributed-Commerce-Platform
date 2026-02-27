package com.aegis.orderservice.client;

/**
 * Thrown for 4xx responses from dependency calls. Not retried by Resilience4j.
 */
public class ClientErrorException extends RuntimeException {

    public ClientErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
