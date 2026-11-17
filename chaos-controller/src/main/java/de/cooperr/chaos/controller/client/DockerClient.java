package de.cooperr.chaos.controller.client;

import de.cooperr.chaos.controller.config.ChaosControllerProperties;
import de.cooperr.chaos.controller.dto.ContainerAction;
import de.cooperr.chaos.controller.dto.ContainerState;
import de.cooperr.chaos.controller.exception.ContainerConflictException;
import de.cooperr.chaos.controller.exception.DockerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Calls the Docker Engine API, in the containers through the socket proxy.
 * <p>
 * Containers are addressed by name. The paths have no version prefix, so Docker uses its current API version.
 */
@Component
public class DockerClient {

    private static final Logger log = LoggerFactory.getLogger(DockerClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    // stopping waits up to 10 seconds for the container to exit
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final RestClient restClient;

    @Autowired
    public DockerClient(ChaosControllerProperties properties) {
        this(RestClient.builder()
                .baseUrl(properties.dockerUrl())
                .requestFactory(createRequestFactory())
                .build());
    }

    DockerClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Reads the state of a container.
     * <p>
     * Never throws: if Docker is unreachable or does not know the container, the state is {@link ContainerState#UNKNOWN}.
     *
     * @param container the name of the container
     * @return the state of the container
     */
    public ContainerState getState(String container) {
        try {
            JsonNode inspection = restClient.get()
                    .uri("/containers/{container}/json", container)
                    .retrieve()
                    .body(JsonNode.class);
            if (inspection == null) {
                return ContainerState.UNKNOWN;
            }
            return ContainerState.fromDocker(inspection.path("State").path("Status").asString(""));
        } catch (RestClientException e) {
            log.debug("Container state unknown: container={}", container, e);
            return ContainerState.UNKNOWN;
        }
    }

    /**
     * Starts, stops, pauses or unpauses a container.
     * <p>
     * Starting a running or stopping a stopped container is not an error (Docker answers {@code 304}).
     *
     * @param container the name of the container
     * @param action    what to do with the container
     * @throws ContainerConflictException if Docker refuses the action, e.g. pausing a stopped container
     * @throws DockerException            if Docker is unreachable or fails otherwise
     */
    public void perform(String container, ContainerAction action) {
        try {
            restClient.post()
                    .uri("/containers/{container}/{action}", container, action.path())
                    .retrieve()
                    .onStatus(status -> status.isSameCodeAs(HttpStatus.CONFLICT), (_, _) -> {
                        throw new ContainerConflictException(container, action);
                    })
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new DockerException(e);
        }
    }

    private static JdkClientHttpRequestFactory createRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }
}
