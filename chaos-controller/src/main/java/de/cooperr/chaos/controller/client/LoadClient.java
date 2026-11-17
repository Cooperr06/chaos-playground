package de.cooperr.chaos.controller.client;

import de.cooperr.chaos.controller.config.ChaosControllerProperties;
import de.cooperr.chaos.controller.dto.LoadSettings;
import de.cooperr.chaos.controller.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Calls the load endpoint ({@code /load}) of the load generator.
 */
@Component
public class LoadClient {

    private static final String SERVICE = "load-generator";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient;

    @Autowired
    public LoadClient(ChaosControllerProperties properties) {
        this(RestClient.builder()
                .baseUrl(properties.loadGeneratorUrl())
                .requestFactory(createRequestFactory())
                .build());
    }

    LoadClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Reads the current rate of the load generator.
     *
     * @throws ServiceUnavailableException if the load generator does not answer or answers with an error
     */
    public LoadSettings getSettings() {
        return call(() -> restClient.get()
                .uri("/load")
                .retrieve()
                .body(LoadSettings.class));
    }

    /**
     * Replaces the rate of the load generator.
     *
     * @return the rate as returned by the load generator
     * @throws ServiceUnavailableException if the load generator does not answer or answers with an error
     */
    public LoadSettings updateSettings(LoadSettings settings) {
        return call(() -> restClient.post()
                .uri("/load")
                .contentType(MediaType.APPLICATION_JSON)
                .body(settings)
                .retrieve()
                .body(LoadSettings.class));
    }

    private static LoadSettings call(Supplier<LoadSettings> request) {
        try {
            LoadSettings settings = request.get();
            if (settings == null) {
                throw new ServiceUnavailableException(SERVICE, new IllegalStateException("Empty response"));
            }
            return settings;
        } catch (RestClientException e) {
            throw new ServiceUnavailableException(SERVICE, e);
        }
    }

    private static JdkClientHttpRequestFactory createRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT);
        return requestFactory;
    }
}
