package de.cooperr.chaos.loadgenerator.client;

import de.cooperr.chaos.loadgenerator.dto.OrderOutcome;
import de.cooperr.chaos.loadgenerator.dto.OrderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class OrderClientTest {

    private static final String BASE_URL = "http://shop-service";

    private MockRestServiceServer server;
    private OrderClient orderClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        orderClient = new OrderClient(builder.build());
    }

    @Test
    void send_created_returnsResponseWithStatus() {
        server.expect(requestTo("%s/orders".formatted(BASE_URL)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.productId").value("watch-1"))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.totalAmount").value(99.90))
                .andRespond(withStatus(HttpStatus.CREATED));

        OrderResult result = orderClient.send(BASE_URL);

        assertEquals(OrderOutcome.RESPONSE, result.outcome());
        assertEquals(201, result.statusCode());
        server.verify();
    }

    @Test
    void send_badGateway_returnsResponseWithStatus() {
        server.expect(requestTo("%s/orders".formatted(BASE_URL)))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        OrderResult result = orderClient.send(BASE_URL);

        assertEquals(OrderOutcome.RESPONSE, result.outcome());
        assertEquals(502, result.statusCode());
    }

    @Test
    void send_timeout_returnsTimeout() {
        server.expect(requestTo("%s/orders".formatted(BASE_URL)))
                .andRespond(withException(new HttpTimeoutException("request timed out")));

        OrderResult result = orderClient.send(BASE_URL);

        assertEquals(OrderOutcome.TIMEOUT, result.outcome());
        assertEquals(0, result.statusCode());
    }

    @Test
    void send_connectionFailure_returnsConnectionError() {
        server.expect(requestTo("%s/orders".formatted(BASE_URL)))
                .andRespond(withException(new IOException("connection refused")));

        OrderResult result = orderClient.send(BASE_URL);

        assertEquals(OrderOutcome.CONNECTION_ERROR, result.outcome());
        assertEquals(0, result.statusCode());
    }
}
