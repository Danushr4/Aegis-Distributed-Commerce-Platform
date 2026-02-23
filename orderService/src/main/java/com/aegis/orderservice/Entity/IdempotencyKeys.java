package com.aegis.orderservice.Entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeys {

    @Id
    private String key;

    private String requestHash;

    @Lob
    private String responseBody;

    private Integer statusCode;

    private LocalDateTime createdAt;
}
