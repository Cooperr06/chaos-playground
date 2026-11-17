package de.cooperr.chaos.controller.client;

import de.cooperr.chaos.controller.dto.LoadSettings;
import de.cooperr.chaos.controller.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LoadClientTest {

    private static final String BASE_URL = "http://load-generator:8091";

    private MockRestServiceServer server;
    private LoadClient loadClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        loadClient = new LoadClient(builder.build());
    }

    @Test
    void getSettings_fullStatus_returnsRate() {
        String responseBody = """
                {"ratePerSecond": 10, "targets": [{"url": "http://shop-plain:8080", "sent": 120, "inFlight": 3,
                 "statusCounts": {"201": 117}, "timeouts": 0, "connectionErrors": 0,
                 "averageDurationMs": 35, "maxDurationMs": 512}]}
                """;

        server.expect(requestTo("%s/load".formatted(BASE_URL)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertEquals(new LoadSettings(10), loadClient.getSettings());
        server.verify();
    }

    @Test
    void updateSettings_validRate_postsRate() {
        String responseBody = """
                {"ratePerSecond": 20, "targets": []}
                """;

        server.expect(requestTo("%s/load".formatted(BASE_URL)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ratePerSecond").value(20))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertEquals(new LoadSettings(20), loadClient.updateSettings(new LoadSettings(20)));
        server.verify();
    }

    @Test
    void getSettings_unreachable_throwsServiceUnavailableException() {
        server.expect(requestTo("%s/load".formatted(BASE_URL)))
                .andRespond(withException(new IOException("connection refused")));

        ServiceUnavailableException exception = assertThrows(ServiceUnavailableException.class, loadClient::getSettings);
        assertEquals("load-generator", exception.getService());
    }
}
