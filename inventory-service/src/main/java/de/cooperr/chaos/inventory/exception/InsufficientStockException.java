package de.cooperr.chaos.inventory.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class InsufficientStockException extends ErrorResponseException {

    public InsufficientStockException(String productId, int requested, int available) {
        super(HttpStatus.CONFLICT);
        setDetail("Insufficient stock for %s: requested %d, available %d".formatted(productId, requested, available));
    }
}
