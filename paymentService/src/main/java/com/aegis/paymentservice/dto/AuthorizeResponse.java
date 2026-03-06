package com.aegis.paymentservice.dto;

import java.util.UUID;

public record AuthorizeResponse(
		UUID orderId,
		UUID authorizationId,
		String status
) {
	public static final String STATUS_AUTHORIZED = "AUTHORIZED";
	public static final String STATUS_FAILED = "FAILED";
}
