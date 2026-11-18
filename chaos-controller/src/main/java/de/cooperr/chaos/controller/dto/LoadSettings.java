package de.cooperr.chaos.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record LoadSettings(@Min(0) @Max(500) int ratePerSecond) {
}
