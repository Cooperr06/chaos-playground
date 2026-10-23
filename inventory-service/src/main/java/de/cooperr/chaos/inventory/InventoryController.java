package de.cooperr.chaos.inventory;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/inventory/{productId}")
    public InventoryResponse getStock(@PathVariable String productId) {
        int stock = inventoryService.getStock(productId);
        return new InventoryResponse(productId, stock);
    }

    @PostMapping("/inventory/{productId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public Reservation reserve(@PathVariable String productId, @Valid @RequestBody ReservationRequest reservation) {
        return inventoryService.reserve(productId, reservation.quantity());
    }
}
