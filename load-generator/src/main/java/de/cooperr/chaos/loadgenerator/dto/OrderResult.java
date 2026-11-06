package de.cooperr.chaos.loadgenerator.dto;

import java.time.Duration;

public record OrderResult(OrderOutcome outcome, int statusCode, Duration duration) {
}
