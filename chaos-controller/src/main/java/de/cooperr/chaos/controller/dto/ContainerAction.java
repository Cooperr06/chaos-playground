package de.cooperr.chaos.controller.dto;

import java.util.Locale;

public enum ContainerAction {

    START,
    STOP,
    PAUSE,
    UNPAUSE,
    ;

    /**
     * Returns the last path segment of the Docker API endpoint, e.g. {@code stop}.
     */
    public String path() {
        return name().toLowerCase(Locale.ROOT);
    }
}
