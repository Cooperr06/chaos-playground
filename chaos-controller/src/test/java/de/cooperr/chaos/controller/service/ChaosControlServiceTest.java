package de.cooperr.chaos.controller.service;

import de.cooperr.chaos.controller.client.ChaosClient;
import de.cooperr.chaos.controller.client.DockerClient;
import de.cooperr.chaos.controller.config.ChaosControllerProperties;
import de.cooperr.chaos.controller.config.ChaosControllerProperties.ServiceTarget;
import de.cooperr.chaos.controller.dto.ChaosSettings;
import de.cooperr.chaos.controller.dto.ContainerAction;
import de.cooperr.chaos.controller.dto.ContainerState;
import de.cooperr.chaos.controller.dto.ServiceStatus;
import de.cooperr.chaos.controller.exception.ServiceNotFoundException;
import de.cooperr.chaos.controller.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChaosControlServiceTest {

    private static final ServiceTarget PAYMENT =
            new ServiceTarget("payment-service", "chaos-playground-payment", "http://payment-service:8082");
    private static final ServiceTarget INVENTORY =
            new ServiceTarget("inventory-service", "chaos-playground-inventory", "http://inventory-service:8081");
    private static final ChaosSettings NO_CHAOS = new ChaosSettings(0, 0);

    private DockerClient dockerClient;
    private ChaosClient chaosClient;
    private ChaosControlService service;

    @BeforeEach
    void setUp() {
        dockerClient = mock(DockerClient.class);
        chaosClient = mock(ChaosClient.class);
        ChaosControllerProperties properties = new ChaosControllerProperties("http://socket-proxy:2375", List.of(PAYMENT, INVENTORY));
        service = new ChaosControlService(properties, dockerClient, chaosClient);
    }

    @Test
    void getServices_allRunning_returnsStateAndChaos() {
        ChaosSettings paymentChaos = new ChaosSettings(5000, 0);

        when(dockerClient.getState(anyString())).thenReturn(ContainerState.RUNNING);
        when(chaosClient.getSettings(PAYMENT)).thenReturn(paymentChaos);
        when(chaosClient.getSettings(INVENTORY)).thenReturn(NO_CHAOS);

        List<ServiceStatus> services = service.getServices();

        assertEquals(List.of(
                new ServiceStatus(PAYMENT.name(), ContainerState.RUNNING, paymentChaos),
                new ServiceStatus(INVENTORY.name(), ContainerState.RUNNING, NO_CHAOS)
        ), services);
    }

    @Test
    void getServices_serviceUnreachable_returnsNullChaos() {
        when(dockerClient.getState(anyString())).thenReturn(ContainerState.UNKNOWN);
        when(chaosClient.getSettings(PAYMENT))
                .thenThrow(new ServiceUnavailableException(PAYMENT.name(), new RuntimeException("connection refused")));
        when(chaosClient.getSettings(INVENTORY)).thenReturn(NO_CHAOS);

        List<ServiceStatus> services = service.getServices();

        assertAll(
                () -> assertEquals(ContainerState.UNKNOWN, services.getFirst().containerState()),
                () -> assertNull(services.getFirst().chaos()),
                () -> assertEquals(NO_CHAOS, services.getLast().chaos())
        );
    }

    @Test
    void getServices_containerPaused_skipsChaosEndpoint() {
        when(dockerClient.getState(PAYMENT.container())).thenReturn(ContainerState.PAUSED);
        when(dockerClient.getState(INVENTORY.container())).thenReturn(ContainerState.EXITED);

        List<ServiceStatus> services = service.getServices();

        assertAll(
                () -> assertNull(services.getFirst().chaos()),
                () -> assertNull(services.getLast().chaos())
        );
        verify(chaosClient, never()).getSettings(any());
    }

    @Test
    void updateChaos_knownService_returnsUpdatedSettings() {
        ChaosSettings settings = new ChaosSettings(5000, 0.3);

        when(chaosClient.updateSettings(PAYMENT, settings)).thenReturn(settings);

        assertEquals(settings, service.updateChaos(PAYMENT.name(), settings));
    }

    @Test
    void updateChaos_unknownService_throwsServiceNotFoundException() {
        assertThrows(ServiceNotFoundException.class, () -> service.updateChaos("shop-service", NO_CHAOS));
        verify(chaosClient, never()).updateSettings(any(), any());
    }

    @Test
    void changeContainer_stop_performsActionAndReturnsNewState() {
        when(dockerClient.getState(INVENTORY.container())).thenReturn(ContainerState.EXITED);

        ServiceStatus status = service.changeContainer(INVENTORY.name(), ContainerAction.STOP);

        verify(dockerClient).perform(INVENTORY.container(), ContainerAction.STOP);
        assertEquals(new ServiceStatus(INVENTORY.name(), ContainerState.EXITED, null), status);
    }

    @Test
    void changeContainer_unknownService_throwsServiceNotFoundException() {
        assertThrows(ServiceNotFoundException.class, () -> service.changeContainer("shop-service", ContainerAction.STOP));
        verify(dockerClient, never()).perform(anyString(), any());
    }
}
