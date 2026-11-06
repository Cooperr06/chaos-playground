package de.cooperr.chaos.inventory.controller;

import de.cooperr.chaos.inventory.dto.ChaosSettings;
import de.cooperr.chaos.inventory.service.ChaosService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChaosController.class)
class ChaosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChaosService chaosService;

    @Test
    void getSettings_settingsSet_returnsSettings() throws Exception {
        ChaosSettings settings = new ChaosSettings(200, 0.5);

        when(chaosService.getSettings()).thenReturn(settings);

        mockMvc.perform(get("/chaos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latencyMs").value(settings.latencyMs()))
                .andExpect(jsonPath("$.errorRate").value(settings.errorRate()));
    }

    @Test
    void updateSettings_validSettings_returnsOk() throws Exception {
        ChaosSettings settings = new ChaosSettings(200, 0.5);
        String requestBody = """
                {"latencyMs": %d, "errorRate": %s}
                """.formatted(settings.latencyMs(), settings.errorRate());

        when(chaosService.updateSettings(settings)).thenReturn(settings);

        mockMvc.perform(post("/chaos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latencyMs").value(settings.latencyMs()))
                .andExpect(jsonPath("$.errorRate").value(settings.errorRate()));
    }

    @Test
    void updateSettings_negativeLatency_returnsBadRequest() throws Exception {
        int latencyMs = -1;
        double errorRate = 0.5;
        String requestBody = """
                {"latencyMs": %d, "errorRate": %s}
                """.formatted(latencyMs, errorRate);

        mockMvc.perform(post("/chaos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(chaosService, never()).updateSettings(any());
    }

    @Test
    void updateSettings_latencyTooHigh_returnsBadRequest() throws Exception {
        int latencyMs = 30001;
        double errorRate = 0.5;
        String requestBody = """
                {"latencyMs": %d, "errorRate": %s}
                """.formatted(latencyMs, errorRate);

        mockMvc.perform(post("/chaos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(chaosService, never()).updateSettings(any());
    }

    @Test
    void updateSettings_negativeErrorRate_returnsBadRequest() throws Exception {
        int latencyMs = 200;
        double errorRate = -0.1;
        String requestBody = """
                {"latencyMs": %d, "errorRate": %s}
                """.formatted(latencyMs, errorRate);

        mockMvc.perform(post("/chaos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(chaosService, never()).updateSettings(any());
    }

    @Test
    void updateSettings_errorRateTooHigh_returnsBadRequest() throws Exception {
        int latencyMs = 200;
        double errorRate = 1.1;
        String requestBody = """
                {"latencyMs": %d, "errorRate": %s}
                """.formatted(latencyMs, errorRate);

        mockMvc.perform(post("/chaos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(chaosService, never()).updateSettings(any());
    }

    @Test
    void updateSettings_missingErrorRate_returnsBadRequest() throws Exception {
        int latencyMs = 200;
        String requestBody = """
                {"latencyMs": %d}
                """.formatted(latencyMs);

        mockMvc.perform(post("/chaos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(chaosService, never()).updateSettings(any());
    }
}
