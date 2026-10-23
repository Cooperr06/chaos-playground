package de.cooperr.chaos.shop.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ProductNotFoundException extends ErrorResponseException {

    public ProductNotFoundException(String productId) {
        super(HttpStatus.NOT_FOUND);
        setDetail("Unknown product: %s".formatted(productId));
    }
}
