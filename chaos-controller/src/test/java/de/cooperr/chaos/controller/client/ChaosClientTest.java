package de.cooperr.chaos.controller.client;

import de.cooperr.chaos.controller.config.ChaosControllerProperties.ServiceTarget;
import de.cooperr.chaos.controller.dto.ChaosSettings;
import de.cooperr.chaos.controller.exception.ServiceUnavailableException;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ChaosClientTest {

    private static final ServiceTarget SERVICE =
            new ServiceTarget("payment-service", "chaos-playground-payment", "http://payment-service");

    private MockRestServiceServer server;
    private ChaosClient chaosClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        chaosClient = new ChaosClient(builder.build());
    }

    @Test
    void getSettings_validResponse_returnsSettings() {
        String responseBody = """
                {"latencyMs": 3000, "errorRate": 0.5}
                """;

        server.expect(requestTo("%s/chaos".formatted(SERVICE.url())))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        ChaosSettings settings = chaosClient.getSettings(SERVICE);

        assertEquals(new ChaosSettings(3000, 0.5), settings);
        server.verify();
    }

    @Test
    void updateSettings_validSettings_postsAndReturnsSettings() {
        int latencyMs = 5000;
        double errorRate = 0.3;
        String responseBody = """
                {"latencyMs": %d, "errorRate": %s}
                """.formatted(latencyMs, errorRate);

        server.expect(requestTo("%s/chaos".formatted(SERVICE.url())))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.latencyMs").value(latencyMs))
                .andExpect(jsonPath("$.errorRate").value(errorRate))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        ChaosSettings settings = chaosClient.updateSettings(SERVICE, new ChaosSettings(latencyMs, errorRate));

        assertEquals(new ChaosSettings(latencyMs, errorRate), settings);
        server.verify();
    }

    @Test
    void getSettings_serviceUnreachable_throwsServiceUnavailableException() {
        server.expect(requestTo("%s/chaos".formatted(SERVICE.url())))
                .andRespond(withException(new IOException("connection refused")));

        ServiceUnavailableException exception =
                assertThrows(ServiceUnavailableException.class, () -> chaosClient.getSettings(SERVICE));
        assertEquals(SERVICE.name(), exception.getService());
    }

    @Test
    void updateSettings_serverError_throwsServiceUnavailableException() {
        server.expect(requestTo("%s/chaos".formatted(SERVICE.url())))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(ServiceUnavailableException.class,
                () -> chaosClient.updateSettings(SERVICE, new ChaosSettings(0, 0)));
    }
}
