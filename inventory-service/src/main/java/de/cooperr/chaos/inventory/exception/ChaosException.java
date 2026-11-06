package de.cooperr.chaos.inventory.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ChaosException extends ErrorResponseException {

    public static final String DETAIL_MESSAGE = "This exception has been thrown on purpose.";

    public ChaosException() {
        super(HttpStatus.INTERNAL_SERVER_ERROR);
        setDetail(DETAIL_MESSAGE);
    }
}
