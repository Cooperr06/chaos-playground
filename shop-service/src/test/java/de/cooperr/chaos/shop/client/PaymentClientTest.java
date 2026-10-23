package de.cooperr.chaos.shop.client;

import de.cooperr.chaos.shop.exception.DownstreamServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PaymentClientTest {

    private static final String BASE_URL = "http://payment-service";

    private MockRestServiceServer server;
    private PaymentClient paymentClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        paymentClient = new PaymentClient(builder.build());
    }

    @Test
    void pay_validRequest_returnsPaymentId() {
        UUID orderId = UUID.randomUUID();
        BigDecimal totalAmount = new BigDecimal("99.90");
        UUID paymentId = UUID.randomUUID();
        String responseBody = """
                {"paymentId": "%s", "orderId": "%s", "status": "APPROVED"}
                """.formatted(paymentId, orderId);

        server.expect(requestTo("%s/payments".formatted(BASE_URL)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.totalAmount").value(totalAmount.doubleValue()))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));

        UUID result = paymentClient.pay(orderId, totalAmount);

        assertEquals(paymentId, result);
        server.verify();
    }

    @Test
    void pay_serverError_throwsDownstreamServiceException() {
        UUID orderId = UUID.randomUUID();
        BigDecimal totalAmount = new BigDecimal("99.90");

        server.expect(requestTo("%s/payments".formatted(BASE_URL)))
                .andRespond(withServerError());

        DownstreamServiceException exception = assertThrows(DownstreamServiceException.class,
                () -> paymentClient.pay(orderId, totalAmount));

        assertEquals("payment-service", exception.getService());
    }

    @Test
    void pay_clientError_throwsDownstreamServiceException() {
        UUID orderId = UUID.randomUUID();
        BigDecimal totalAmount = new BigDecimal("99.90");

        server.expect(requestTo("%s/payments".formatted(BASE_URL)))
                .andRespond(withBadRequest());

        assertThrows(DownstreamServiceException.class, () -> paymentClient.pay(orderId, totalAmount));
    }

    @Test
    void pay_connectionFailure_throwsDownstreamServiceException() {
        UUID orderId = UUID.randomUUID();
        BigDecimal totalAmount = new BigDecimal("99.90");

        server.expect(requestTo("%s/payments".formatted(BASE_URL)))
                .andRespond(withException(new IOException("connection refused")));

        assertThrows(DownstreamServiceException.class, () -> paymentClient.pay(orderId, totalAmount));
    }

    @Test
    void pay_emptyResponse_throwsDownstreamServiceException() {
        UUID orderId = UUID.randomUUID();
        BigDecimal totalAmount = new BigDecimal("99.90");

        server.expect(requestTo("%s/payments".formatted(BASE_URL)))
                .andRespond(withStatus(HttpStatus.CREATED));

        assertThrows(DownstreamServiceException.class, () -> paymentClient.pay(orderId, totalAmount));
    }
}
