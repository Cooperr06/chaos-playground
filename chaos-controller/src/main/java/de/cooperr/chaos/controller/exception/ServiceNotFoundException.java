package de.cooperr.chaos.controller.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ServiceNotFoundException extends ErrorResponseException {

    public ServiceNotFoundException(String name) {
        super(HttpStatus.NOT_FOUND);
        setDetail("Unknown service: %s".formatted(name));
    }
}
