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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Controls the configured services: their chaos settings and their containers.
 */
@Service
public class ChaosControlService {

    private static final Logger log = LoggerFactory.getLogger(ChaosControlService.class);

    private final List<ServiceTarget> services;
    private final DockerClient dockerClient;
    private final ChaosClient chaosClient;

    public ChaosControlService(ChaosControllerProperties properties, DockerClient dockerClient, ChaosClient chaosClient) {
        this.services = List.copyOf(properties.services());
        this.dockerClient = dockerClient;
        this.chaosClient = chaosClient;
    }

    /**
     * Returns the state of all services. An unreachable service or Docker API is not an error here.
     */
    public List<ServiceStatus> getServices() {
        return services.stream().map(this::toStatus).toList();
    }

    public ChaosSettings updateChaos(String name, ChaosSettings settings) {
        ServiceTarget service = find(name);
        ChaosSettings updatedSettings = chaosClient.updateSettings(service, settings);
        log.info("Chaos settings updated: service={}, latencyMs={}, errorRate={}",
                name, updatedSettings.latencyMs(), updatedSettings.errorRate());
        return updatedSettings;
    }

    public ServiceStatus changeContainer(String name, ContainerAction action) {
        ServiceTarget service = find(name);
        dockerClient.perform(service.container(), action);
        log.info("Container changed: service={}, container={}, action={}", name, service.container(), action);
        return toStatus(service);
    }

    private ServiceTarget find(String name) {
        return services.stream()
                .filter(service -> service.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ServiceNotFoundException(name));
    }

    private ServiceStatus toStatus(ServiceTarget service) {
        ContainerState state = dockerClient.getState(service.container());
        return new ServiceStatus(service.name(), state, readChaos(service, state));
    }

    /**
     * Asks the service only if it can answer, a paused service would make every overview wait for the timeout.
     */
    private @Nullable ChaosSettings readChaos(ServiceTarget service, ContainerState state) {
        if (state != ContainerState.RUNNING && state != ContainerState.UNKNOWN) {
            return null;
        }
        try {
            return chaosClient.getSettings(service);
        } catch (ServiceUnavailableException e) {
            log.debug("Chaos settings unavailable: service={}", service.name(), e);
            return null;
        }
    }
}
