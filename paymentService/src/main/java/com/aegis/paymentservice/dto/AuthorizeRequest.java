package com.aegis.paymentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AuthorizeRequest(
		@NotNull(message = "orderId is required") UUID orderId,
		@NotNull(message = "amount is required") @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
		@NotBlank(message = "currency is required") String currency
) {}
