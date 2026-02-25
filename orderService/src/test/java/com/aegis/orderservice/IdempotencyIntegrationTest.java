package com.aegis.orderservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Proves the 4 idempotency guarantees:
 * 1) Same key + same request twice → same orderId, no duplicate orders
 * 2) Same key + different request → 409 Conflict
 * 3) Retry with same key → same success response
 * 4) Concurrent duplicate requests with same key → exactly one order created
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class IdempotencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orders_db")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private static String createOrderPayload(String userId, String currency, String sku, int qty, String unitPrice) {
        return """
                {
                  "userId": "%s",
                  "currency": "%s",
                  "items": [
                    { "sku": "%s", "qty": %d, "unitPrice": %s }
                  ]
                }
                """.formatted(userId, currency, sku, qty, unitPrice);
    }

    @Nested
    @DisplayName("1. Same key + same request twice → same orderId, no duplicate")
    class SameKeySameRequest {

        @Test
        void secondCallReturnsSameOrderId() throws Exception {
            String key = "idem-" + UUID.randomUUID();
            String body = createOrderPayload("u1", "USD", "SKU-A", 2, "10.00");

            String first = mockMvc.perform(post("/api/v1/orders")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").exists())
                    .andReturn().getResponse().getContentAsString();

            String second = mockMvc.perform(post("/api/v1/orders")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            // Same response (same orderId)
            String orderIdFirst = first.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");
            String orderIdSecond = second.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");
            assert orderIdFirst.equals(orderIdSecond) : "Expected same orderId: " + first + " vs " + second;
        }
    }

    @Nested
    @DisplayName("2. Same key + different request → 409 Conflict")
    class SameKeyDifferentRequest {

        @Test
        void returns409Conflict() throws Exception {
            String key = "idem-" + UUID.randomUUID();
            String body1 = createOrderPayload("u1", "USD", "SKU-A", 2, "10.00");
            String body2 = createOrderPayload("u1", "USD", "SKU-B", 1, "20.00");

            mockMvc.perform(post("/api/v1/orders")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body1))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/orders")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body2))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("different request")));
        }
    }

    @Nested
    @DisplayName("3. Retry with same key → same success response")
    class RetrySameKey {

        @Test
        void returnsSameSuccessResponse() throws Exception {
            String key = "idem-" + UUID.randomUUID();
            String body = createOrderPayload("u2", "EUR", "SKU-C", 1, "99.99");

            mockMvc.perform(post("/api/v1/orders")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").exists())
                    .andExpect(jsonPath("$.totalAmount").value(99.99));

            // Simulate client retry after timeout
            mockMvc.perform(post("/api/v1/orders")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").exists())
                    .andExpect(jsonPath("$.totalAmount").value(99.99));
        }
    }

    @Nested
    @DisplayName("4. Concurrent duplicate requests with same key → exactly one order")
    class ConcurrentSameKey {

        @Test
        void exactlyOneOrderCreated() throws Exception {
            String key = "idem-concurrent-" + UUID.randomUUID();
            String body = createOrderPayload("u3", "GBP", "SKU-D", 1, "5.00");
            int concurrency = 5;
            CyclicBarrier start = new CyclicBarrier(concurrency);
            Set<String> orderIds = java.util.Collections.synchronizedSet(new HashSet<>());
            AtomicInteger status201 = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(concurrency);

            Future<?>[] futures = new Future<?>[concurrency];
            for (int i = 0; i < concurrency; i++) {
                futures[i] = executor.submit(() -> {
                    try {
                        start.await();
                        ResultActions result = mockMvc.perform(post("/api/v1/orders")
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body));
                        int status = result.andReturn().getResponse().getStatus();
                        if (status == 201) {
                            status201.incrementAndGet();
                            String content = result.andReturn().getResponse().getContentAsString();
                            JsonNode node = objectMapper.readTree(content);
                            if (node.has("orderId")) {
                                orderIds.add(node.get("orderId").asText());
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            for (Future<?> f : futures) {
                f.get();
            }
            executor.shutdown();

            assertThat(status201.get()).as("At least one 201").isGreaterThanOrEqualTo(1);
            assertThat(orderIds).as("Exactly one unique orderId across all 201 responses").hasSize(1);
        }
    }

    @Nested
    @DisplayName("Missing Idempotency-Key → 400 Bad Request")
    class MissingKey {

        @Test
        void returns400WhenHeaderMissing() throws Exception {
            String body = createOrderPayload("u1", "USD", "SKU-A", 1, "10.00");
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Idempotency-Key")));
        }
    }
}
