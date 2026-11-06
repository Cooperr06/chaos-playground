package de.cooperr.chaos.inventory.config;

import de.cooperr.chaos.inventory.dto.ChaosSettings;
import de.cooperr.chaos.inventory.exception.ChaosException;
import de.cooperr.chaos.inventory.service.ChaosService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void getStock_errorRateOne_returnsInternalServerError() throws Exception {
        ChaosSettings chaosSettings = new ChaosSettings(0, 1.0);
        String productId = "watch-1";

        chaosService.updateSettings(chaosSettings);

        mockMvc.perform(get("/inventory/{productId}", productId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value(ChaosException.DETAIL_MESSAGE));
    }

    @Test
    void getStock_errorRateZero_returnsOk() throws Exception {
        ChaosSettings chaosSettings = new ChaosSettings(0, 0.0);
        String productId = "watch-1";

        chaosService.updateSettings(chaosSettings);

        mockMvc.perform(get("/inventory/{productId}", productId))
                .andExpect(status().isOk());
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
