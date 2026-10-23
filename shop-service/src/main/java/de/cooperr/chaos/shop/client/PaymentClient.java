package de.cooperr.chaos.shop.client;

import de.cooperr.chaos.shop.config.ShopProperties;
import de.cooperr.chaos.shop.dto.PaymentRequest;
import de.cooperr.chaos.shop.dto.PaymentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentClient extends ServiceClient {

    private static final String SERVICE = "payment-service";

    @Autowired
    public PaymentClient(ShopProperties properties) {
        super(SERVICE, properties.paymentUrl());
    }

    PaymentClient(RestClient restClient) {
        super(SERVICE, restClient);
    }

    public UUID pay(UUID orderId, BigDecimal totalAmount) {
        PaymentResponse response = call(() -> restClient.post()
                .uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PaymentRequest(orderId.toString(), totalAmount))
                .retrieve()
                .body(PaymentResponse.class));
        return response.paymentId();
    }
}
