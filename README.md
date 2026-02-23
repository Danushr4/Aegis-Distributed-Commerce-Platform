# Aegis Distributed Commerce Platform

A distributed commerce platform built with microservices. This repo contains the Order Service and will grow to include Inventory, Payment, and other bounded contexts.

## Architecture notes

- **Order Service**: Spring Boot (Java 21) microservice owning the order lifecycle. Exposes REST at `/api/v1/orders`.
- **Data**: PostgreSQL for orders and order items; schema managed by Flyway migrations. Redis is present for future use (e.g. caching, idempotency, sessions).
- **Run dependencies**: Start Postgres and Redis via Docker Compose, then run the Order Service.

## Prerequisites

- Java 21
- Docker & Docker Compose (for Postgres and Redis)
- Gradle (or use the wrapper)

## Quick start

1. **Start infrastructure**
   ```bash
   docker-compose up -d
   ```

2. **Run Order Service**
   ```bash
   ./gradlew :orderService:bootRun
   ```
   Or from `orderService`: `./gradlew bootRun`

3. **Check health**
   - Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
   - Dummy orders API: [http://localhost:8080/api/v1/orders](http://localhost:8080/api/v1/orders)

## What's done

| Date       | Done |
| ---------- | ---- |
| 2025-02-23 | Spring Boot Order Service skeleton with Spring Web, JPA, PostgreSQL, Validation, Actuator, Lombok, Flyway, Redis dependency. `/api/v1/orders` controller with dummy GET response; health at `/actuator/health`; global exception handler. Docker Compose with Postgres 16 and Redis 7; `application.yml` config; Flyway migration creating `orders`, `order_items`, and `idempotency_keys` tables. README with architecture notes; security config permitting actuator and API. |
