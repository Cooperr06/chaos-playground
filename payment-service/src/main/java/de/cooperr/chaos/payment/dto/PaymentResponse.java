package de.cooperr.chaos.payment.dto;

import java.util.UUID;

public record PaymentResponse(UUID paymentId, String orderId, PaymentStatus status) {
}
