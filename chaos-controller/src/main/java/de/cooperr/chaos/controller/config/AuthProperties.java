package de.cooperr.chaos.controller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which Authelia groups get which role in the controller, so existing groups of the homelab can be used.
 *
 * @param adminGroup  members may change chaos, containers and load, e.g. {@code chaos-admin}
 * @param viewerGroup members may only look, e.g. {@code chaos-viewer}
 */
@ConfigurationProperties("chaos-controller.auth")
public record AuthProperties(String adminGroup, String viewerGroup) {
}
