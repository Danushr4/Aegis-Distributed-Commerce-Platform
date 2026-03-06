package com.aegis.paymentservice.controller;

import com.aegis.paymentservice.dto.AuthorizeRequest;
import com.aegis.paymentservice.dto.AuthorizeResponse;
import com.aegis.paymentservice.dto.CaptureRequest;
import com.aegis.paymentservice.dto.CaptureResponse;
import com.aegis.paymentservice.service.MockPaymentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentsController {

	private final MockPaymentService mockPaymentService;

	public PaymentsController(MockPaymentService mockPaymentService) {
		this.mockPaymentService = mockPaymentService;
	}

	/**
	 * Authorize payment for an order.
	 * Force behavior via header X-Mock-Result: success | fail | timeout | 503 | 502 | 500
	 */
	@PostMapping(value = "/authorize", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<AuthorizeResponse> authorize(
			@RequestHeader(value = "X-Mock-Result", required = false) String mockResult,
			@Valid @RequestBody AuthorizeRequest request) {
		AuthorizeResponse response = mockPaymentService.authorize(request, mockResult);
		return ResponseEntity.ok(response);
	}

	/**
	 * Capture a previously authorized payment (optional; for later use).
	 * Force behavior via header X-Mock-Result: success | fail | timeout | 503 | 502 | 500
	 */
	@PostMapping(value = "/capture", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<CaptureResponse> capture(
			@RequestHeader(value = "X-Mock-Result", required = false) String mockResult,
			@Valid @RequestBody CaptureRequest request) {
		CaptureResponse response = mockPaymentService.capture(request, mockResult);
		return ResponseEntity.ok(response);
	}
}
