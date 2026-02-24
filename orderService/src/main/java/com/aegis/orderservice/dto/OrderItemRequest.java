package com.aegis.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequest {

    @NotBlank(message = "sku must not be blank")
    private String sku;

    @NotNull(message = "qty is required")
    @Min(value = 1, message = "qty must be at least 1")
    private Integer qty;

    @NotNull(message = "unitPrice is required")
    @Positive(message = "unitPrice must be greater than 0")
    private BigDecimal unitPrice;
}
