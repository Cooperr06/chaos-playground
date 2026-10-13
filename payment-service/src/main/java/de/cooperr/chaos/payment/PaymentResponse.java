package de.cooperr.chaos.payment;

import java.util.UUID;

public record PaymentResponse(UUID paymentId, String orderId, PaymentStatus status) {
}
