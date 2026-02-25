package com.aegis.orderservice.repository;

import com.aegis.orderservice.Entity.IdempotencyKeys;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeysRepository extends JpaRepository<IdempotencyKeys, String> {
}
