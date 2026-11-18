package de.cooperr.chaos.inventory.service;

import de.cooperr.chaos.inventory.config.InventoryProperties;
import de.cooperr.chaos.inventory.dto.Reservation;
import de.cooperr.chaos.inventory.exception.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InventoryServiceTest {

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        InventoryProperties inventory = new InventoryProperties(Set.of("a", "b"));
        inventoryService = new InventoryService(inventory);
    }

    @Test
    void reserve_existingProduct_returnsReservation() {
        String productId = "a";
        int quantity = 2;

        Reservation reservation = inventoryService.reserve(productId, quantity);

        assertAll(
                () -> assertNotNull(reservation.reservationId()),
                () -> assertEquals(productId, reservation.productId()),
                () -> assertEquals(quantity, reservation.quantity())
        );
    }

    @Test
    void reserve_sameProductTwice_returnsDifferentReservations() {
        String productId = "a";

        Reservation first = inventoryService.reserve(productId, 1);
        Reservation second = inventoryService.reserve(productId, 1);

        assertNotEquals(first.reservationId(), second.reservationId());
    }

    @Test
    void reserve_largeQuantity_returnsReservation() {
        String productId = "b";
        int quantity = Integer.MAX_VALUE;

        Reservation reservation = inventoryService.reserve(productId, quantity);

        assertEquals(quantity, reservation.quantity());
    }

    @Test
    void reserve_zeroQuantity_throwsIllegalArgumentException() {
        String productId = "a";
        int quantity = 0;

        assertThrows(IllegalArgumentException.class, () -> inventoryService.reserve(productId, quantity));
    }

    @Test
    void reserve_nonExistingProduct_throwsProductNotFoundException() {
        String nonExistingProduct = "nonExistingProduct";
        int quantity = 2;

        assertThrows(ProductNotFoundException.class, () -> inventoryService.reserve(nonExistingProduct, quantity));
    }
}
