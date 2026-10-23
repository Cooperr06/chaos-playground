package de.cooperr.chaos.inventory.controller;

import de.cooperr.chaos.inventory.dto.Reservation;
import de.cooperr.chaos.inventory.exception.InsufficientStockException;
import de.cooperr.chaos.inventory.exception.ProductNotFoundException;
import de.cooperr.chaos.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    @Test
    void getStock_existingProduct_returnsStock() throws Exception {
        String productId = "a";
        int quantity = 1;

        when(inventoryService.getStock(productId)).thenReturn(quantity);

        mockMvc.perform(get("/inventory/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.available").value(quantity));
    }

    @Test
    void getStock_nonExistingProduct_returnsNotFound() throws Exception {
        String productId = "unknown";

        when(inventoryService.getStock(productId)).thenThrow(new ProductNotFoundException(productId));

        mockMvc.perform(get("/inventory/{productId}", productId))
                .andExpect(status().isNotFound());
    }

    @Test
    void reserve_sufficientStock_returnsCreatedReservation() throws Exception {
        String productId = "a";
        int quantity = 2;
        UUID reservationId = UUID.randomUUID();
        String requestBody = """
                {"quantity": %d}
                """.formatted(quantity);

        when(inventoryService.reserve(productId, quantity)).thenReturn(new Reservation(reservationId, productId, quantity));

        mockMvc.perform(post("/inventory/{productId}/reservations", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()))
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.quantity").value(quantity));
    }

    @Test
    void reserve_zeroQuantity_returnsBadRequest() throws Exception {
        String productId = "a";
        int quantity = 0;
        String requestBody = """
                {"quantity": %d}
                """.formatted(quantity);

        mockMvc.perform(post("/inventory/{productId}/reservations", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(inventoryService, never()).reserve(anyString(), anyInt());
    }

    @Test
    void reserve_negativeQuantity_returnsBadRequest() throws Exception {
        String productId = "a";
        int quantity = -1;
        String requestBody = """
                {"quantity": %d}
                """.formatted(quantity);

        mockMvc.perform(post("/inventory/{productId}/reservations", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(inventoryService, never()).reserve(anyString(), anyInt());
    }

    @Test
    void reserve_missingQuantity_returnsBadRequest() throws Exception {
        String productId = "a";
        String requestBody = "{}";

        mockMvc.perform(post("/inventory/{productId}/reservations", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(inventoryService, never()).reserve(anyString(), anyInt());
    }

    @Test
    void reserve_nonExistingProduct_returnsNotFound() throws Exception {
        String productId = "unknown";
        int quantity = 2;
        String requestBody = """
                {"quantity": %d}
                """.formatted(quantity);

        when(inventoryService.reserve(productId, quantity)).thenThrow(new ProductNotFoundException(productId));

        mockMvc.perform(post("/inventory/{productId}/reservations", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void reserve_insufficientStock_returnsConflict() throws Exception {
        String productId = "a";
        int quantity = 7;
        int available = 5;
        String requestBody = """
                {"quantity": %d}
                """.formatted(quantity);

        when(inventoryService.reserve(productId, quantity)).thenThrow(new InsufficientStockException(productId, quantity, available));

        mockMvc.perform(post("/inventory/{productId}/reservations", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }
}
