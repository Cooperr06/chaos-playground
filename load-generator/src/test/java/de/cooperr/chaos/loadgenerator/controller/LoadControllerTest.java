package de.cooperr.chaos.loadgenerator.controller;

import de.cooperr.chaos.loadgenerator.dto.LoadStatus;
import de.cooperr.chaos.loadgenerator.dto.TargetStatus;
import de.cooperr.chaos.loadgenerator.service.LoadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
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
    private LoadService loadService;

    @Test
    void getStatus_rateSet_returnsStatus() throws Exception {
        TargetStatus target = new TargetStatus("http://shop-service:8080", 120, 50,
                Map.of(201, 60L, 502, 10L), 0, 0, 35, 5012);
        LoadStatus status = new LoadStatus(10, List.of(target));

        when(loadService.getStatus()).thenReturn(status);

        mockMvc.perform(get("/load"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratePerSecond").value(status.ratePerSecond()))
                .andExpect(jsonPath("$.targets[0].url").value(target.url()))
                .andExpect(jsonPath("$.targets[0].inFlight").value(target.inFlight()))
                .andExpect(jsonPath("$.targets[0].statusCounts.201").value(60))
                .andExpect(jsonPath("$.targets[0].averageDurationMs").value(target.averageDurationMs()));
    }

    @Test
    void updateRate_validRate_returnsOk() throws Exception {
        int ratePerSecond = 10;
        String requestBody = """
                {"ratePerSecond": %d}
                """.formatted(ratePerSecond);

        when(loadService.updateRate(ratePerSecond)).thenReturn(new LoadStatus(ratePerSecond, List.of()));

        mockMvc.perform(post("/load")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratePerSecond").value(ratePerSecond));

        verify(loadService).updateRate(ratePerSecond);
    }

    @Test
    void updateRate_negativeRate_returnsBadRequest() throws Exception {
        String requestBody = """
                {"ratePerSecond": %d}
                """.formatted(-1);

        mockMvc.perform(post("/load")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(loadService, never()).updateRate(anyInt());
    }

    @Test
    void updateRate_rateTooHigh_returnsBadRequest() throws Exception {
        String requestBody = """
                {"ratePerSecond": %d}
                """.formatted(101);

        mockMvc.perform(post("/load")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(loadService, never()).updateRate(anyInt());
    }

    @Test
    void updateRate_missingRate_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/load")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(loadService, never()).updateRate(anyInt());
    }
}
