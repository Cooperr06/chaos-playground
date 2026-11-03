package de.cooperr.chaos.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ChaosException extends ErrorResponseException {

    public ChaosException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR);
        setDetail("This exception has been thrown on purpose.");
    }
}
