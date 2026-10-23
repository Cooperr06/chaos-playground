package de.cooperr.chaos.shop.dto;

import java.util.UUID;

public record PaymentResponse(UUID paymentId, String orderId, String status) {
}
