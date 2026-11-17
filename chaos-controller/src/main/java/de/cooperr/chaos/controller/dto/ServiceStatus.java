package de.cooperr.chaos.controller.dto;

import org.jspecify.annotations.Nullable;

/**
 * State of one controllable service.
 *
 * @param chaos the chaos settings of the service, or {@code null} if the service does not answer
 */
public record ServiceStatus(String name, ContainerState containerState, @Nullable ChaosSettings chaos) {
}
