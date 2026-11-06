package de.cooperr.chaos.inventory.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ChaosSettings(
        @Min(0) @Max(30000) int latencyMs,
        @DecimalMin("0.0") @DecimalMax("1.0") double errorRate) {

    public static final ChaosSettings DEFAULT = new ChaosSettings(0, 0);
}
