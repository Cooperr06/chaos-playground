package de.cooperr.chaos.inventory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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
}
