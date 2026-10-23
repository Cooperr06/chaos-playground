package de.cooperr.chaos.shop.client;

import de.cooperr.chaos.shop.config.ShopProperties;
import de.cooperr.chaos.shop.dto.ReservationRequest;
import de.cooperr.chaos.shop.dto.ReservationResponse;
import de.cooperr.chaos.shop.exception.InsufficientStockException;
import de.cooperr.chaos.shop.exception.ProductNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class InventoryClient extends ServiceClient {

    private static final String SERVICE = "inventory-service";

    @Autowired
    public InventoryClient(ShopProperties properties) {
        super(SERVICE, properties.inventoryUrl());
    }

    InventoryClient(RestClient restClient) {
        super(SERVICE, restClient);
    }

    public UUID reserve(String productId, int quantity) {
        ReservationResponse response = call(() -> restClient.post()
                .uri("/inventory/{productId}/reservations", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReservationRequest(quantity))
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (_, _) -> {
                    throw new ProductNotFoundException(productId);
                })
                .onStatus(status -> status.isSameCodeAs(HttpStatus.CONFLICT), (_, _) -> {
                    throw new InsufficientStockException(productId, quantity);
                })
                .body(ReservationResponse.class));
        return response.reservationId();
    }
}
