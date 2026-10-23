package de.cooperr.chaos.inventory.service;

import de.cooperr.chaos.inventory.config.InventoryProperties;
import de.cooperr.chaos.inventory.dto.Reservation;
import de.cooperr.chaos.inventory.exception.InsufficientStockException;
import de.cooperr.chaos.inventory.exception.ProductNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private final ConcurrentMap<String, Integer> stock;

    public InventoryService(InventoryProperties inventory) {
        this.stock = new ConcurrentHashMap<>(inventory.initialStock());
    }

    public int getStock(String productId) {
        Integer productStock = stock.get(productId);
        if (productStock == null) {
            throw new ProductNotFoundException(productId);
        }
        return productStock;
    }

    public Reservation reserve(String productId, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        int remaining = stock.compute(productId, (_, v) -> {
            if (v == null) {
                throw new ProductNotFoundException(productId);
            }

            if (v >= quantity) {
                return v - quantity;
            } else {
                throw new InsufficientStockException(productId, quantity, v);
            }
        });

        Reservation reservation = new Reservation(UUID.randomUUID(), productId, quantity);
        log.info("Reserved productId={} quantity={} remaining={}", productId, quantity, remaining);
        return reservation;
    }
}
