package de.cooperr.chaos.inventory.dto;

import jakarta.validation.constraints.Positive;

public record ReservationRequest(@Positive int quantity) {
}
