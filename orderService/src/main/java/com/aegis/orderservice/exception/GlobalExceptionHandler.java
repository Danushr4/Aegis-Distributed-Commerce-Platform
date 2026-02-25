package com.aegis.orderservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

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
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyStillProcessingException.class)
    public ResponseEntity<Map<String, String>> handleIdempotencyStillProcessing(IdempotencyStillProcessingException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    public ResponseEntity<Map<String, String>> handleMissingIdempotencyKey(MissingIdempotencyKeyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception ex) {
        return ResponseEntity.internalServerError().body("Error: " + ex.getMessage());
    }

    public record ValidationErrorResponse(String message, List<FieldError> errors) {}

    public record FieldError(String field, String message) {}
}
