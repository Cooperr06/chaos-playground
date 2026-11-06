package de.cooperr.chaos.loadgenerator.dto;

import java.math.BigDecimal;

public record OrderRequest(String productId, int quantity, BigDecimal totalAmount) {
}
