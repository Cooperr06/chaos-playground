package de.cooperr.chaos.shop.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class DownstreamServiceException extends ErrorResponseException {

    private final String service;

    public DownstreamServiceException(String service, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, cause);
        this.service = service;
        setDetail("Service unavailable: %s".formatted(service));
    }

    public String getService() {
        return service;
    }
}
