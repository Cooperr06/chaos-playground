package de.cooperr.chaos.shop.service;

import de.cooperr.chaos.shop.client.InventoryClient;
import de.cooperr.chaos.shop.client.PaymentClient;
import de.cooperr.chaos.shop.config.ShopProperties;
import de.cooperr.chaos.shop.dto.OrderRequest;
import de.cooperr.chaos.shop.dto.OrderResponse;
import de.cooperr.chaos.shop.dto.OrderStatus;
import de.cooperr.chaos.shop.exception.DownstreamServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final boolean resilienceEnabled;

    public OrderService(InventoryClient inventoryClient, PaymentClient paymentClient, ShopProperties properties) {
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.resilienceEnabled = properties.resilienceEnabled();
    }

    public OrderResponse placeOrder(OrderRequest orderRequest) {
        UUID orderId = UUID.randomUUID();
        try {
            UUID reservationId = inventoryClient.reserve(orderRequest.productId(), orderRequest.quantity());
            return pay(orderId, orderRequest, reservationId);
        } catch (DownstreamServiceException e) {
            log.warn("Order failed: orderId={}, service={}", orderId, e.getService());
            throw e;
        }
    }

    /**
     * Pays a reserved order.
     * <p>
     * With resilience enabled, a failed payment does not fail the order: the reservation stays and
     * the order is answered as {@link OrderStatus#PAYMENT_PENDING} ("payment will be retried").
     * Without resilience, the failure is passed on.
     */
    private OrderResponse pay(UUID orderId, OrderRequest orderRequest, UUID reservationId) {
        try {
            UUID paymentId = paymentClient.pay(orderId, orderRequest.totalAmount());

            log.info("Order confirmed: orderId={}, productId={}, quantity={}",
                    orderId, orderRequest.productId(), orderRequest.quantity());
            return new OrderResponse(orderId, orderRequest.productId(), orderRequest.quantity(),
                    reservationId, paymentId, OrderStatus.CONFIRMED);
        } catch (DownstreamServiceException e) {
            if (!resilienceEnabled) {
                throw e;
            }
            log.warn("Payment pending: orderId={}, service={}", orderId, e.getService());
            return new OrderResponse(orderId, orderRequest.productId(), orderRequest.quantity(),
                    reservationId, null, OrderStatus.PAYMENT_PENDING);
        }
    }
}
