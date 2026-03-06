package com.aegis.orderservice.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public record AuthorizeRequestDto(
		@JsonProperty("orderId") UUID orderId,
		@JsonProperty("amount") BigDecimal amount,
		@JsonProperty("currency") String currency
) {}
