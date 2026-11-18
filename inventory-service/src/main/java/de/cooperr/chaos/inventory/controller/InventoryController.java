package de.cooperr.chaos.inventory.controller;

import de.cooperr.chaos.inventory.dto.Reservation;
import de.cooperr.chaos.inventory.dto.ReservationRequest;
import de.cooperr.chaos.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/{productId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public Reservation reserve(@PathVariable String productId, @Valid @RequestBody ReservationRequest reservation) {
        return inventoryService.reserve(productId, reservation.quantity());
    }
}
