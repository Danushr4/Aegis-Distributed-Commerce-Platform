package com.aegis.paymentservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CaptureRequest(
		@NotNull(message = "orderId is required") UUID orderId,
		@NotNull(message = "authorizationId is required") UUID authorizationId
) {}
