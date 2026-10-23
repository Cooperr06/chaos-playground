package de.cooperr.chaos.shop.controller;

import de.cooperr.chaos.shop.dto.OrderRequest;
import de.cooperr.chaos.shop.dto.OrderResponse;
import de.cooperr.chaos.shop.dto.OrderStatus;
import de.cooperr.chaos.shop.exception.DownstreamServiceException;
import de.cooperr.chaos.shop.exception.InsufficientStockException;
import de.cooperr.chaos.shop.exception.ProductNotFoundException;
import de.cooperr.chaos.shop.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void createOrder_validRequest_returnsCreatedOrder() throws Exception {
        String productId = "a";
        int quantity = 2;
        BigDecimal totalAmount = new BigDecimal("99.90");
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String requestBody = """
                {"productId": "%s", "quantity": %d, "totalAmount": %s}
                """.formatted(productId, quantity, totalAmount);

        when(orderService.placeOrder(any(OrderRequest.class)))
                .thenReturn(new OrderResponse(orderId, productId, quantity, reservationId, paymentId, OrderStatus.CONFIRMED));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.quantity").value(quantity))
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void createOrder_zeroQuantity_returnsBadRequest() throws Exception {
        String productId = "a";
        int quantity = 0;
        BigDecimal totalAmount = new BigDecimal("99.90");
        String requestBody = """
                {"productId": "%s", "quantity": %d, "totalAmount": %s}
                """.formatted(productId, quantity, totalAmount);

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).placeOrder(any());
    }

    @Test
    void createOrder_zeroTotalAmount_returnsBadRequest() throws Exception {
        String productId = "a";
        int quantity = 2;
        BigDecimal totalAmount = BigDecimal.ZERO;
        String requestBody = """
                {"productId": "%s", "quantity": %d, "totalAmount": %s}
                """.formatted(productId, quantity, totalAmount);

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).placeOrder(any());
    }

    @Test
    void createOrder_missingProductId_returnsBadRequest() throws Exception {
        int quantity = 2;
        BigDecimal totalAmount = new BigDecimal("99.90");
        String requestBody = """
                {"quantity": %d, "totalAmount": %s}
                """.formatted(quantity, totalAmount);

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).placeOrder(any());
    }

    @Test
    void createOrder_nonExistingProduct_returnsNotFound() throws Exception {
        String productId = "unknown";
        int quantity = 2;
        BigDecimal totalAmount = new BigDecimal("99.90");
        String requestBody = """
                {"productId": "%s", "quantity": %d, "totalAmount": %s}
                """.formatted(productId, quantity, totalAmount);

        when(orderService.placeOrder(any(OrderRequest.class))).thenThrow(new ProductNotFoundException(productId));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void createOrder_insufficientStock_returnsConflict() throws Exception {
        String productId = "a";
        int quantity = 60;
        BigDecimal totalAmount = new BigDecimal("99.90");
        String requestBody = """
                {"productId": "%s", "quantity": %d, "totalAmount": %s}
                """.formatted(productId, quantity, totalAmount);

        when(orderService.placeOrder(any(OrderRequest.class))).thenThrow(new InsufficientStockException(productId, quantity));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    void createOrder_downstreamFailure_returnsBadGateway() throws Exception {
        String productId = "a";
        int quantity = 2;
        BigDecimal totalAmount = new BigDecimal("99.90");
        String requestBody = """
                {"productId": "%s", "quantity": %d, "totalAmount": %s}
                """.formatted(productId, quantity, totalAmount);

        when(orderService.placeOrder(any(OrderRequest.class)))
                .thenThrow(new DownstreamServiceException("payment-service", new RuntimeException("connection refused")));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadGateway());
    }
}
