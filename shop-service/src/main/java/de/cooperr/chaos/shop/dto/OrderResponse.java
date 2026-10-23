package de.cooperr.chaos.shop.dto;

import java.util.UUID;

public record OrderResponse(UUID orderId, String productId, int quantity,
                            UUID reservationId, UUID paymentId, OrderStatus status) {
}
