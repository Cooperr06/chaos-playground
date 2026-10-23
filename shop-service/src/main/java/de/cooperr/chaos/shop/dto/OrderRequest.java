package de.cooperr.chaos.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderRequest(
        @NotBlank String productId,
        @Positive int quantity,
        @NotNull @Positive BigDecimal totalAmount) {
}
