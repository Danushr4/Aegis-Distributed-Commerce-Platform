package com.aegis.orderservice.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        ValidationErrorResponse body = new ValidationErrorResponse("Validation failed", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, String>> handleIdempotencyConflict(IdempotencyConflictException ex) {
        log.warn("Idempotency conflict: exceptionClass={} message={} correlationId={}",
                ex.getClass().getSimpleName(), ex.getMessage(), MDC.get(com.aegis.orderservice.filter.CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyStillProcessingException.class)
    public ResponseEntity<Map<String, String>> handleIdempotencyStillProcessing(IdempotencyStillProcessingException ex) {
        log.warn("Idempotency still processing: correlationId={}", MDC.get(com.aegis.orderservice.filter.CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    public ResponseEntity<Map<String, String>> handleMissingIdempotencyKey(MissingIdempotencyKeyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ServiceOverloadedException.class)
    public ResponseEntity<Map<String, String>> handleServiceOverloaded(ServiceOverloadedException ex) {
        log.warn("Service overloaded (backpressure): message={} correlationId={}", ex.getMessage(), MDC.get(com.aegis.orderservice.filter.CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception ex) {
        String correlationId = MDC.get(com.aegis.orderservice.filter.CorrelationIdFilter.MDC_KEY);
        String orderId = MDC.get("orderId");
        log.error("Unhandled exception: exceptionClass={} message={} correlationId={} orderId={}",
                ex.getClass().getName(),
                ex.getMessage(),
                correlationId,
                orderId,
                ex);
        return ResponseEntity.internalServerError().body("Error: " + ex.getMessage());
    }

    public record ValidationErrorResponse(String message, List<FieldError> errors) {}

    public record FieldError(String field, String message) {}
}
