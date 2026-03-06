package com.aegis.orderservice.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AuthorizeResponseDto(
		@JsonProperty("orderId") UUID orderId,
		@JsonProperty("authorizationId") UUID authorizationId,
		@JsonProperty("status") String status
) {}
