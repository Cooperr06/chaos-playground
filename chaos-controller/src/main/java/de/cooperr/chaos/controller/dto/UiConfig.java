package de.cooperr.chaos.controller.dto;

/**
 * Settings the web page needs from the server.
 *
 * @param grafanaUrl the URL of Grafana as seen by the browser
 */
public record UiConfig(String grafanaUrl) {
}
