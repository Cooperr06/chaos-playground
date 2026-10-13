package de.cooperr.chaos.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void approvesValidPayment() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId": "o-1", "amount": 49.90}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.orderId").value("o-1"))
                .andExpect(jsonPath("$.paymentId").isNotEmpty());
    }

    @Test
    void rejectsZeroAmount() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId": "o-1", "amount": 0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativeAmount() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId": "o-1", "amount": -5}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingOrderId() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 49.90}
                                """))
                .andExpect(status().isBadRequest());
    }
}
