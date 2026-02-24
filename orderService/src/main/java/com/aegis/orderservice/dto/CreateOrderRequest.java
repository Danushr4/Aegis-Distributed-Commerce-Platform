package com.aegis.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotBlank(message = "userId must not be blank")
    private String userId;

    @NotBlank(message = "currency must not be blank")
    @Size(max = 3, message = "currency must be at most 3 characters")
    private String currency;

    @NotEmpty(message = "items must not be empty")
    @Valid
    private List<OrderItemRequest> items;
}
