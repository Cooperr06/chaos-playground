package de.cooperr.chaos.shop.dto;

import java.math.BigDecimal;

public record PaymentRequest(String orderId, BigDecimal totalAmount) {
}
