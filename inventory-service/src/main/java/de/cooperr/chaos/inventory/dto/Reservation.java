package de.cooperr.chaos.inventory.dto;

import java.util.UUID;

public record Reservation(UUID reservationId, String productId, int quantity) {
}
