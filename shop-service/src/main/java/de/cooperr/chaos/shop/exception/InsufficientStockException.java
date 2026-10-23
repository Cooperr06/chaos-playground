package de.cooperr.chaos.shop.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class InsufficientStockException extends ErrorResponseException {

    public InsufficientStockException(String productId, int quantity) {
        super(HttpStatus.CONFLICT);
        setDetail("Insufficient stock for %s: requested %d".formatted(productId, quantity));
    }
}
