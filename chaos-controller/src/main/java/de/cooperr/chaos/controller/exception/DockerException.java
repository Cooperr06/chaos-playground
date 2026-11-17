package de.cooperr.chaos.controller.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class DockerException extends ErrorResponseException {

    public DockerException(Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, cause);
        setDetail("Docker API unavailable");
    }
}
