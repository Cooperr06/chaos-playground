package de.cooperr.chaos.controller.dto;

import java.util.Locale;

public enum ContainerState {

    CREATED,
    RUNNING,
    PAUSED,
    RESTARTING,
    REMOVING,
    EXITED,
    DEAD,
    UNKNOWN,
    ;

    /**
     * Maps the {@code State.Status} of the Docker API, e.g. {@code running}.
     *
     * @param status the status as reported by Docker
     * @return the matching state, {@link #UNKNOWN} for anything else
     */
    public static ContainerState fromDocker(String status) {
        try {
            return valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
