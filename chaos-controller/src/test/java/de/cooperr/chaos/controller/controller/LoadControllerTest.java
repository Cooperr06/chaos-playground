package de.cooperr.chaos.controller.controller;

import de.cooperr.chaos.controller.dto.LoadSettings;
import de.cooperr.chaos.controller.exception.ServiceUnavailableException;
import de.cooperr.chaos.controller.service.ChaosControlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoadController.class)
class LoadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChaosControlService chaosControlService;

    @Test
    void getLoad_rateSet_returnsRate() throws Exception {
        when(chaosControlService.getLoad()).thenReturn(new LoadSettings(10));

        mockMvc.perform(get("/load"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratePerSecond").value(10));
    }

    @Test
    void updateLoad_validRate_returnsOk() throws Exception {
        int ratePerSecond = 20;
        String requestBody = """
                {"ratePerSecond": %d}
                """.formatted(ratePerSecond);

        when(chaosControlService.updateLoad(new LoadSettings(ratePerSecond))).thenReturn(new LoadSettings(ratePerSecond));

        mockMvc.perform(post("/load")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratePerSecond").value(ratePerSecond));
    }

    @Test
    void updateLoad_rateTooHigh_returnsBadRequest() throws Exception {
        String requestBody = """
                {"ratePerSecond": %d}
                """.formatted(501);

        mockMvc.perform(post("/load")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(chaosControlService, never()).updateLoad(any());
    }

    @Test
    void updateLoad_loadGeneratorUnreachable_returnsBadGateway() throws Exception {
        String requestBody = """
                {"ratePerSecond": %d}
                """.formatted(10);

        when(chaosControlService.updateLoad(any()))
                .thenThrow(new ServiceUnavailableException("load-generator", new RuntimeException("connection refused")));

        mockMvc.perform(post("/load")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadGateway());
    }
}
