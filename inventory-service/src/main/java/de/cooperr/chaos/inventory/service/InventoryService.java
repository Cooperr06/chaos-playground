package de.cooperr.chaos.inventory.service;

import de.cooperr.chaos.inventory.config.InventoryProperties;
import de.cooperr.chaos.inventory.dto.Reservation;
import de.cooperr.chaos.inventory.exception.ProductNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Reserves products for orders.
 * <p>
 * There is no stock: every known product can always be reserved. The inventory exists to be sabotaged, a stock
 * that runs empty under constant load would only distort the demo.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final Set<String> products;

    public InventoryService(InventoryProperties inventory) {
        this.products = Set.copyOf(inventory.products());
    }

    public Reservation reserve(String productId, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (!products.contains(productId)) {
            throw new ProductNotFoundException(productId);
        }

        Reservation reservation = new Reservation(UUID.randomUUID(), productId, quantity);
        log.debug("Reserved productId={} quantity={}", productId, quantity);
        return reservation;
    }
}
