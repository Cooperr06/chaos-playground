package de.cooperr.chaos.controller.client;

import de.cooperr.chaos.controller.dto.ContainerAction;
import de.cooperr.chaos.controller.dto.ContainerState;
import de.cooperr.chaos.controller.exception.ContainerConflictException;
import de.cooperr.chaos.controller.exception.DockerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DockerClientTest {

    private static final String BASE_URL = "http://socket-proxy:2375";
    private static final String CONTAINER = "chaos-playground-payment";

    private MockRestServiceServer server;
    private DockerClient dockerClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        dockerClient = new DockerClient(builder.build());
    }

    @Test
    void getState_running_returnsRunning() {
        String responseBody = """
                {"Id": "4fa6e0f0c678", "Name": "/%s", "State": {"Status": "running", "Running": true, "Paused": false}}
                """.formatted(CONTAINER);

        server.expect(requestTo("%s/containers/%s/json".formatted(BASE_URL, CONTAINER)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertEquals(ContainerState.RUNNING, dockerClient.getState(CONTAINER));
        server.verify();
    }

    @Test
    void getState_unknownStatus_returnsUnknown() {
        String responseBody = """
                {"State": {"Status": "sleeping"}}
                """;

        server.expect(requestTo("%s/containers/%s/json".formatted(BASE_URL, CONTAINER)))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertEquals(ContainerState.UNKNOWN, dockerClient.getState(CONTAINER));
    }

    @Test
    void getState_noSuchContainer_returnsUnknown() {
        server.expect(requestTo("%s/containers/%s/json".formatted(BASE_URL, CONTAINER)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals(ContainerState.UNKNOWN, dockerClient.getState(CONTAINER));
    }

    @Test
    void getState_dockerUnreachable_returnsUnknown() {
        server.expect(requestTo("%s/containers/%s/json".formatted(BASE_URL, CONTAINER)))
                .andRespond(withException(new IOException("connection refused")));

        assertEquals(ContainerState.UNKNOWN, dockerClient.getState(CONTAINER));
    }

    @Test
    void perform_stop_postsToContainer() {
        server.expect(requestTo("%s/containers/%s/stop".formatted(BASE_URL, CONTAINER)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        dockerClient.perform(CONTAINER, ContainerAction.STOP);

        server.verify();
    }

    @Test
    void perform_alreadyStopped_doesNotThrow() {
        server.expect(requestTo("%s/containers/%s/stop".formatted(BASE_URL, CONTAINER)))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED));

        dockerClient.perform(CONTAINER, ContainerAction.STOP);

        server.verify();
    }

    @Test
    void perform_conflict_throwsContainerConflictException() {
        server.expect(requestTo("%s/containers/%s/pause".formatted(BASE_URL, CONTAINER)))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThrows(ContainerConflictException.class, () -> dockerClient.perform(CONTAINER, ContainerAction.PAUSE));
    }

    @Test
    void perform_serverError_throwsDockerException() {
        server.expect(requestTo("%s/containers/%s/start".formatted(BASE_URL, CONTAINER)))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(DockerException.class, () -> dockerClient.perform(CONTAINER, ContainerAction.START));
    }

    @Test
    void perform_forbiddenByProxy_throwsDockerException() {
        server.expect(requestTo("%s/containers/%s/unpause".formatted(BASE_URL, CONTAINER)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(DockerException.class, () -> dockerClient.perform(CONTAINER, ContainerAction.UNPAUSE));
    }
}
