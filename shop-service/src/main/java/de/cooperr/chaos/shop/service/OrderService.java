package de.cooperr.chaos.shop.service;

import de.cooperr.chaos.shop.client.InventoryClient;
import de.cooperr.chaos.shop.client.PaymentClient;
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

    public OrderService(InventoryClient inventoryClient, PaymentClient paymentClient) {
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
    }

    public OrderResponse placeOrder(OrderRequest orderRequest) {
        UUID orderId = UUID.randomUUID();
        try {
            UUID reservationId = inventoryClient.reserve(orderRequest.productId(), orderRequest.quantity());
            UUID paymentId = paymentClient.pay(orderId, orderRequest.totalAmount());

            log.info("Order confirmed: orderId={}, productId={}, quantity={}",
                    orderId, orderRequest.productId(), orderRequest.quantity());
            return new OrderResponse(orderId, orderRequest.productId(), orderRequest.quantity(),
                    reservationId, paymentId, OrderStatus.CONFIRMED);
        } catch (DownstreamServiceException e) {
            log.warn("Order failed: orderId={}, service={}", orderId, e.getService());
            throw e;
        }
    }
}
