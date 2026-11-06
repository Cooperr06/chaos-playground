package de.cooperr.chaos.loadgenerator.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record LoadSettings(@Min(0) @Max(100) int ratePerSecond) {
}
