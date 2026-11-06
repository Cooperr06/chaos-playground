package de.cooperr.chaos.payment.config;

import de.cooperr.chaos.payment.dto.ChaosSettings;
import de.cooperr.chaos.payment.exception.ChaosException;
import de.cooperr.chaos.payment.service.ChaosService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Isolated
@SpringBootTest
@AutoConfigureMockMvc
class ChaosInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChaosService chaosService;

    @AfterEach
    void tearDown() {
        chaosService.updateSettings(ChaosSettings.DEFAULT);
    }

    @Test
    void pay_errorRateOne_returnsInternalServerError() throws Exception {
        ChaosSettings chaosSettings = new ChaosSettings(0, 1.0);
        String requestBody = """
                {"orderId": "a", "totalAmount": 5}
                """;

        chaosService.updateSettings(chaosSettings);

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value(ChaosException.DETAIL_MESSAGE));
    }

    @Test
    void pay_errorRateZero_returnsCreated() throws Exception {
        ChaosSettings chaosSettings = new ChaosSettings(0, 0.0);
        String requestBody = """
                {"orderId": "a", "totalAmount": 5}
                """;

        chaosService.updateSettings(chaosSettings);

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void getChaosSettings_errorRateOne_returnsOk() throws Exception {
        ChaosSettings chaosSettings = new ChaosSettings(0, 1.0);

        chaosService.updateSettings(chaosSettings);

        mockMvc.perform(get("/chaos"))
                .andExpect(status().isOk());
    }

    @Test
    void health_errorRateOne_returnsOk() throws Exception {
        ChaosSettings chaosSettings = new ChaosSettings(0, 1.0);

        chaosService.updateSettings(chaosSettings);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
