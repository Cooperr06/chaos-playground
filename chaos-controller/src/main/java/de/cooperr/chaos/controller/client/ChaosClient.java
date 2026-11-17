package de.cooperr.chaos.controller.client;

import de.cooperr.chaos.controller.config.ChaosControllerProperties.ServiceTarget;
import de.cooperr.chaos.controller.dto.ChaosSettings;
import de.cooperr.chaos.controller.exception.ServiceUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Calls the chaos endpoint ({@code /chaos}) of a service.
 */
@Component
public class ChaosClient {

    // a paused container accepts connections but never answers
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient;

    public ChaosClient() {
        this(RestClient.builder().requestFactory(createRequestFactory()).build());
    }

    ChaosClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Reads the chaos settings of a service.
     *
     * @throws ServiceUnavailableException if the service does not answer or answers with an error
     */
    public ChaosSettings getSettings(ServiceTarget service) {
        return call(service, () -> restClient.get()
                .uri("%s/chaos".formatted(service.url()))
                .retrieve()
                .body(ChaosSettings.class));
    }

    /**
     * Replaces the chaos settings of a service.
     *
     * @return the settings as returned by the service
     * @throws ServiceUnavailableException if the service does not answer or answers with an error
     */
    public ChaosSettings updateSettings(ServiceTarget service, ChaosSettings settings) {
        return call(service, () -> restClient.post()
                .uri("%s/chaos".formatted(service.url()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(settings)
                .retrieve()
                .body(ChaosSettings.class));
    }

    private static ChaosSettings call(ServiceTarget service, Supplier<ChaosSettings> request) {
        try {
            ChaosSettings settings = request.get();
            if (settings == null) {
                throw new ServiceUnavailableException(service.name(), new IllegalStateException("Empty response"));
            }
            return settings;
        } catch (RestClientException e) {
            throw new ServiceUnavailableException(service.name(), e);
        }
    }

    private static JdkClientHttpRequestFactory createRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT);
        return requestFactory;
    }
}
