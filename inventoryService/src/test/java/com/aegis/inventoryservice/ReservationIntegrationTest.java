package com.aegis.inventoryservice;

import com.aegis.inventoryservice.entity.Inventory;
import com.aegis.inventoryservice.repository.InventoryRepository;
import com.aegis.inventoryservice.repository.ReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves: reserve + release work; DB reflects correctly; idempotent reserve; negative stock prevented.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReservationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inventory_db")
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
    InventoryRepository inventoryRepository;
    @Autowired
    ReservationRepository reservationRepository;

    @Test
    @DisplayName("Reserve then release: DB reflects correctly")
    void reserveThenRelease_reflectsInDb() throws Exception {
        UUID orderId = UUID.randomUUID();
        String body = """
                {"orderId":"%s","items":[{"sku":"SKU-001","qty":10}]}
                """.formatted(orderId);

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.items[0].qty").value(10));

        assertThat(reservationRepository.findByOrderId(orderId)).hasSize(1);
        Inventory inv = inventoryRepository.findById("SKU-001").orElseThrow();
        assertThat(inv.getAvailableQty()).isEqualTo(90); // 100 - 10

        mockMvc.perform(delete("/api/v1/reservations/{orderId}", orderId))
                .andExpect(status().isNoContent());

        assertThat(reservationRepository.findByOrderId(orderId)).isEmpty();
        inv = inventoryRepository.findById("SKU-001").orElseThrow();
        assertThat(inv.getAvailableQty()).isEqualTo(100);
    }

    @Test
    @DisplayName("Same reserve request twice is idempotent (no double-reserve)")
    void reserveTwiceSameOrderId_idempotent() throws Exception {
        UUID orderId = UUID.randomUUID();
        String body = """
                {"orderId":"%s","items":[{"sku":"SKU-002","qty":5}]}
                """.formatted(orderId);

        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        assertThat(reservationRepository.findByOrderId(orderId)).hasSize(1);
        Inventory inv = inventoryRepository.findById("SKU-002").orElseThrow();
        assertThat(inv.getAvailableQty()).isEqualTo(45); // 50 - 5 once, not 50 - 10
    }

    @Test
    @DisplayName("Insufficient stock returns 409")
    void reserveInsufficientStock_returns409() throws Exception {
        UUID orderId = UUID.randomUUID();
        String body = """
                {"orderId":"%s","items":[{"sku":"SKU-001","qty":1000}]}
                """.formatted(orderId);

        mockMvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Insufficient")));

        assertThat(reservationRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    @DisplayName("GET inventory/{sku} returns available qty")
    void getInventory_returnsAvailableQty() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/SKU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-001"))
                .andExpect(jsonPath("$.availableQty").isNumber());
        mockMvc.perform(get("/api/v1/inventory/NONEXISTENT"))
                .andExpect(status().isNotFound());
    }
}
