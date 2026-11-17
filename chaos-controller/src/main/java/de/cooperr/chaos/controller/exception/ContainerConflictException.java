package de.cooperr.chaos.controller.exception;

import de.cooperr.chaos.controller.dto.ContainerAction;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ContainerConflictException extends ErrorResponseException {

    public ContainerConflictException(String container, ContainerAction action) {
        super(HttpStatus.CONFLICT);
        setDetail("Cannot %s container: %s".formatted(action.path(), container));
    }
}
