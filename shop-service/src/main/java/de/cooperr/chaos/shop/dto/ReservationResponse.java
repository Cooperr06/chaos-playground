package de.cooperr.chaos.shop.dto;

import java.util.UUID;

public record ReservationResponse(UUID reservationId, String productId, int quantity) {
}
