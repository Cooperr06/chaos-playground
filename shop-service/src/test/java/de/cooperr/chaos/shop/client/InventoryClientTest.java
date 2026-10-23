package de.cooperr.chaos.shop.client;

import de.cooperr.chaos.shop.exception.DownstreamServiceException;
import de.cooperr.chaos.shop.exception.InsufficientStockException;
import de.cooperr.chaos.shop.exception.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class InventoryClientTest {

    private static final String BASE_URL = "http://inventory-service";

    private MockRestServiceServer server;
    private InventoryClient inventoryClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        inventoryClient = new InventoryClient(builder.build());
    }

    @Test
    void reserve_validRequest_returnsReservationId() {
        String productId = "a";
        int quantity = 2;
        UUID reservationId = UUID.randomUUID();
        String responseBody = """
                {"reservationId": "%s", "productId": "%s", "quantity": %d}
                """.formatted(reservationId, productId, quantity);

        server.expect(requestTo("%s/inventory/%s/reservations".formatted(BASE_URL, productId)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.quantity").value(quantity))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));

        UUID result = inventoryClient.reserve(productId, quantity);

        assertEquals(reservationId, result);
        server.verify();
    }

    @Test
    void reserve_nonExistingProduct_throwsProductNotFoundException() {
        String productId = "unknown";
        int quantity = 2;

        server.expect(requestTo("%s/inventory/%s/reservations".formatted(BASE_URL, productId)))
                .andRespond(withResourceNotFound());

        assertThrows(ProductNotFoundException.class, () -> inventoryClient.reserve(productId, quantity));
    }

    @Test
    void reserve_insufficientStock_throwsInsufficientStockException() {
        String productId = "a";
        int quantity = 60;

        server.expect(requestTo("%s/inventory/%s/reservations".formatted(BASE_URL, productId)))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThrows(InsufficientStockException.class, () -> inventoryClient.reserve(productId, quantity));
    }

    @Test
    void reserve_serverError_throwsDownstreamServiceException() {
        String productId = "a";
        int quantity = 2;

        server.expect(requestTo("%s/inventory/%s/reservations".formatted(BASE_URL, productId)))
                .andRespond(withServerError());

        DownstreamServiceException exception = assertThrows(DownstreamServiceException.class,
                () -> inventoryClient.reserve(productId, quantity));

        assertEquals("inventory-service", exception.getService());
    }

    @Test
    void reserve_otherClientError_throwsDownstreamServiceException() {
        String productId = "a";
        int quantity = 2;

        server.expect(requestTo("%s/inventory/%s/reservations".formatted(BASE_URL, productId)))
                .andRespond(withBadRequest());

        assertThrows(DownstreamServiceException.class, () -> inventoryClient.reserve(productId, quantity));
    }

    @Test
    void reserve_connectionFailure_throwsDownstreamServiceException() {
        String productId = "a";
        int quantity = 2;

        server.expect(requestTo("%s/inventory/%s/reservations".formatted(BASE_URL, productId)))
                .andRespond(withException(new IOException("connection refused")));

        assertThrows(DownstreamServiceException.class, () -> inventoryClient.reserve(productId, quantity));
    }

    @Test
    void reserve_emptyResponse_throwsDownstreamServiceException() {
        String productId = "a";
        int quantity = 2;

        server.expect(requestTo("%s/inventory/%s/reservations".formatted(BASE_URL, productId)))
                .andRespond(withStatus(HttpStatus.CREATED));

        assertThrows(DownstreamServiceException.class, () -> inventoryClient.reserve(productId, quantity));
    }
}
