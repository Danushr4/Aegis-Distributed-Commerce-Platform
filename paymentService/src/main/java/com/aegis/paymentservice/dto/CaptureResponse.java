package com.aegis.paymentservice.dto;

import java.util.UUID;

public record CaptureResponse(
		UUID orderId,
		UUID authorizationId,
		String status
) {
	public static final String STATUS_CAPTURED = "CAPTURED";
	public static final String STATUS_FAILED = "FAILED";
}
