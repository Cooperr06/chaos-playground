package de.cooperr.chaos.inventory;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class InventoryService {

    private final ConcurrentMap<String, Integer> stock;

    public InventoryService(InventoryProperties inventory) {
        this.stock = new ConcurrentHashMap<>(inventory.initialStock());
    }

    public int getStock(String productId) {
        Integer stock = this.stock.get(productId);
        if (stock == null) {
            throw new ProductNotFoundException(productId);
        }
        return stock;
    }
}
