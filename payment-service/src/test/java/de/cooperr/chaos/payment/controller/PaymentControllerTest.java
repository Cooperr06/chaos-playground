package de.cooperr.chaos.payment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pay_validRequest_returnsCreatedPayment() throws Exception {
        String orderId = "o-1";
        BigDecimal totalAmount = new BigDecimal("49.90");
        String requestBody = """
                {"orderId": "%s", "totalAmount": %s}
                """.formatted(orderId, totalAmount);

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.paymentId").isNotEmpty());
    }

    @Test
    void pay_zeroAmount_returnsBadRequest() throws Exception {
        String orderId = "o-1";
        BigDecimal totalAmount = BigDecimal.ZERO;
        String requestBody = """
                {"orderId": "%s", "totalAmount": %s}
                """.formatted(orderId, totalAmount);

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pay_negativeAmount_returnsBadRequest() throws Exception {
        String orderId = "o-1";
        BigDecimal totalAmount = new BigDecimal("-5");
        String requestBody = """
                {"orderId": "%s", "totalAmount": %s}
                """.formatted(orderId, totalAmount);

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pay_missingOrderId_returnsBadRequest() throws Exception {
        BigDecimal totalAmount = new BigDecimal("49.90");
        String requestBody = """
                {"totalAmount": %s}
                """.formatted(totalAmount);

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}
