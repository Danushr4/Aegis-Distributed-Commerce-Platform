package com.aegis.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    private Long id;
    private String sku;
    private Integer qty;
    private BigDecimal unitPrice;
    private BigDecimal lineAmount;
}
