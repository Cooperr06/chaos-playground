package de.cooperr.chaos.shop.dto;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Result of an order.
 *
 * @param paymentId the payment, or {@code null} if the status is {@link OrderStatus#PAYMENT_PENDING}
 */
public record OrderResponse(UUID orderId, String productId, int quantity,
                            UUID reservationId, @Nullable UUID paymentId, OrderStatus status) {
}
