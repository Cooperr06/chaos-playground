package de.cooperr.chaos.inventory;

import jakarta.validation.constraints.Positive;

public record ReservationRequest(@Positive int quantity) {
}
