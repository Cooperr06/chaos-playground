package de.cooperr.chaos.controller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Settings of the chaos controller.
 *
 * @param dockerUrl        the base URL of the Docker API, e.g. {@code http://socket-proxy:2375}
 * @param loadGeneratorUrl the base URL of the load generator, e.g. {@code http://load-generator:8091}
 * @param grafanaUrl       the URL of Grafana as seen by the browser, e.g. {@code /grafana}
 * @param services         the services that can be controlled, nothing else can be reached through the endpoints
 */
@ConfigurationProperties("chaos-controller")
public record ChaosControllerProperties(String dockerUrl, String loadGeneratorUrl, String grafanaUrl,
                                        List<ServiceTarget> services) {

    /**
     * One service that can be controlled.
     *
     * @param name      the name used in the endpoints, e.g. {@code payment-service}
     * @param container the name of its Docker container, e.g. {@code chaos-playground-payment}
     * @param url       the base URL of its chaos endpoint, e.g. {@code http://payment-service:8082}
     */
    public record ServiceTarget(String name, String container, String url) {
    }
}
