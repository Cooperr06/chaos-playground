package de.cooperr.chaos.shop.client;

import de.cooperr.chaos.shop.config.ShopProperties;
import de.cooperr.chaos.shop.dto.PaymentRequest;
import de.cooperr.chaos.shop.dto.PaymentResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentClient extends ServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private static final String SERVICE = "payment-service";

    @Autowired
    public PaymentClient(ShopProperties properties, CircuitBreakerRegistry registry) {
        super(SERVICE, createRestClient(properties), createCircuitBreaker(properties, registry));
    }

    PaymentClient(RestClient restClient) {
        super(SERVICE, restClient);
    }

    PaymentClient(RestClient restClient, CircuitBreaker circuitBreaker) {
        super(SERVICE, restClient, circuitBreaker);
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

    private static RestClient createRestClient(ShopProperties properties) {
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.paymentUrl());

        if (properties.resilienceEnabled()) {
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
            requestFactory.setReadTimeout(properties.paymentTimeout());
            builder.requestFactory(requestFactory);
        }
        return builder.build();
    }

    @Nullable
    private static CircuitBreaker createCircuitBreaker(ShopProperties properties, CircuitBreakerRegistry registry) {
        if (!properties.resilienceEnabled()) {
            return null;
        }

        CircuitBreaker circuitBreaker = registry.circuitBreaker(SERVICE);
        circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.info("Circuit breaker {}: {}", event.getCircuitBreakerName(), event.getStateTransition()));
        return circuitBreaker;
    }
}
