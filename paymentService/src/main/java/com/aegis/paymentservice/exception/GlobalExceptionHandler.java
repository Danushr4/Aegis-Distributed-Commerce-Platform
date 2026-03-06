package com.aegis.paymentservice.exception;

import com.aegis.paymentservice.service.MockPaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MockPaymentService.MockPaymentException.class)
	public ResponseEntity<ErrorBody> handleMockPaymentException(MockPaymentService.MockPaymentException ex) {
		return ResponseEntity
				.status(ex.getHttpStatus())
				.body(new ErrorBody(ex.getMessage()));
	}

	public record ErrorBody(String error) {}
}
