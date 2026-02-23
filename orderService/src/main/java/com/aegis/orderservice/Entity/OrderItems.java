package com.aegis.orderservice.Entity;


import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItems {
    @Id
    @GeneratedValue
    private Long id;

    private String sku;
    private Integer qty;
    private BigDecimal price;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Orders order;
}
