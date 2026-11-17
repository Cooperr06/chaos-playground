package de.cooperr.chaos.controller.controller;

import de.cooperr.chaos.controller.dto.ChaosSettings;
import de.cooperr.chaos.controller.dto.ContainerAction;
import de.cooperr.chaos.controller.dto.ContainerState;
import de.cooperr.chaos.controller.dto.ServiceStatus;
import de.cooperr.chaos.controller.exception.ContainerConflictException;
import de.cooperr.chaos.controller.exception.DockerException;
import de.cooperr.chaos.controller.exception.ServiceNotFoundException;
import de.cooperr.chaos.controller.exception.ServiceUnavailableException;
import de.cooperr.chaos.controller.service.ChaosControlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ServiceController.class)
class ServiceControllerTest {

    private static final String PAYMENT = "payment-service";
    private static final String INVENTORY = "inventory-service";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChaosControlService chaosControlService;

    @Test
    void getServices_oneStopped_returnsStates() throws Exception {
        when(chaosControlService.getServices()).thenReturn(List.of(
                new ServiceStatus(PAYMENT, ContainerState.RUNNING, new ChaosSettings(5000, 0.3)),
                new ServiceStatus(INVENTORY, ContainerState.EXITED, null)));

        mockMvc.perform(get("/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(PAYMENT))
                .andExpect(jsonPath("$[0].containerState").value("RUNNING"))
                .andExpect(jsonPath("$[0].chaos.latencyMs").value(5000))
                .andExpect(jsonPath("$[0].chaos.errorRate").value(0.3))
                .andExpect(jsonPath("$[1].containerState").value("EXITED"))
                .andExpect(jsonPath("$[1].chaos").isEmpty());
    }

    @Test
    void updateChaos_validSettings_returnsOk() throws Exception {
        int latencyMs = 5000;
        double errorRate = 0.3;
        ChaosSettings settings = new ChaosSettings(latencyMs, errorRate);
        String requestBody = """
                {"latencyMs": %d, "errorRate": %s}
                """.formatted(latencyMs, errorRate);

        when(chaosControlService.updateChaos(PAYMENT, settings)).thenReturn(settings);

        mockMvc.perform(post("/services/{name}/chaos", PAYMENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latencyMs").value(latencyMs))
                .andExpect(jsonPath("$.errorRate").value(errorRate));

        verify(chaosControlService).updateChaos(PAYMENT, settings);
    }

    @Test
    void updateChaos_errorRateTooHigh_returnsBadRequest() throws Exception {
        String requestBody = """
                {"latencyMs": %d, "errorRate": %s}
                """.formatted(0, 1.5);

        mockMvc.perform(post("/services/{name}/chaos", PAYMENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(chaosControlService, never()).updateChaos(anyString(), any());
    }

    @Test
    void updateChaos_unknownService_returnsNotFound() throws Exception {
        String requestBody = """
                {"latencyMs": %d, "errorRate": %s}
                """.formatted(0, 0.0);

        when(chaosControlService.updateChaos(anyString(), any())).thenThrow(new ServiceNotFoundException("shop-service"));

        mockMvc.perform(post("/services/{name}/chaos", "shop-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateChaos_serviceUnreachable_returnsBadGateway() throws Exception {
        String requestBody = """
                {"latencyMs": %d, "errorRate": %s}
                """.formatted(0, 0.0);

        when(chaosControlService.updateChaos(anyString(), any()))
                .thenThrow(new ServiceUnavailableException(PAYMENT, new RuntimeException("connection refused")));

        mockMvc.perform(post("/services/{name}/chaos", PAYMENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadGateway());
    }

    @Test
    void stop_knownService_returnsNewState() throws Exception {
        when(chaosControlService.changeContainer(INVENTORY, ContainerAction.STOP))
                .thenReturn(new ServiceStatus(INVENTORY, ContainerState.EXITED, null));

        mockMvc.perform(post("/services/{name}/stop", INVENTORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(INVENTORY))
                .andExpect(jsonPath("$.containerState").value("EXITED"));
    }

    @Test
    void start_knownService_startsContainer() throws Exception {
        when(chaosControlService.changeContainer(INVENTORY, ContainerAction.START))
                .thenReturn(new ServiceStatus(INVENTORY, ContainerState.RUNNING, null));

        mockMvc.perform(post("/services/{name}/start", INVENTORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containerState").value("RUNNING"));
    }

    @Test
    void pause_knownService_pausesContainer() throws Exception {
        when(chaosControlService.changeContainer(PAYMENT, ContainerAction.PAUSE))
                .thenReturn(new ServiceStatus(PAYMENT, ContainerState.PAUSED, null));

        mockMvc.perform(post("/services/{name}/pause", PAYMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containerState").value("PAUSED"));
    }

    @Test
    void unpause_knownService_unpausesContainer() throws Exception {
        when(chaosControlService.changeContainer(PAYMENT, ContainerAction.UNPAUSE))
                .thenReturn(new ServiceStatus(PAYMENT, ContainerState.RUNNING, null));

        mockMvc.perform(post("/services/{name}/unpause", PAYMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containerState").value("RUNNING"));
    }

    @Test
    void stop_unknownService_returnsNotFound() throws Exception {
        when(chaosControlService.changeContainer(anyString(), any())).thenThrow(new ServiceNotFoundException("shop-service"));

        mockMvc.perform(post("/services/{name}/stop", "shop-service"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pause_containerStopped_returnsConflict() throws Exception {
        when(chaosControlService.changeContainer(anyString(), any()))
                .thenThrow(new ContainerConflictException("chaos-playground-payment", ContainerAction.PAUSE));

        mockMvc.perform(post("/services/{name}/pause", PAYMENT))
                .andExpect(status().isConflict());
    }

    @Test
    void stop_dockerUnreachable_returnsBadGateway() throws Exception {
        when(chaosControlService.changeContainer(anyString(), any()))
                .thenThrow(new DockerException(new RuntimeException("connection refused")));

        mockMvc.perform(post("/services/{name}/stop", PAYMENT))
                .andExpect(status().isBadGateway());
    }
}
