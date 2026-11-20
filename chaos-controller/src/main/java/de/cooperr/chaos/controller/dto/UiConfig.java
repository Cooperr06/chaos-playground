package de.cooperr.chaos.controller.dto;

/**
 * Settings the web page needs from the server.
 *
 * @param grafanaUrl the URL of Grafana as seen by the browser
 * @param logoutUrl  the logout page of Authelia, {@code null} if not configured
 */
public record UiConfig(String grafanaUrl, String logoutUrl) {
}
