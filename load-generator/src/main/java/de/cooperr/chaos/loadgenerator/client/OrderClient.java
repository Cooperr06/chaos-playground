package de.cooperr.chaos.loadgenerator.client;

import de.cooperr.chaos.loadgenerator.config.LoadGeneratorProperties;
import de.cooperr.chaos.loadgenerator.dto.OrderOutcome;
import de.cooperr.chaos.loadgenerator.dto.OrderRequest;
import de.cooperr.chaos.loadgenerator.dto.OrderResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

@Component
public class OrderClient {

    private static final OrderRequest ORDER = new OrderRequest("watch-1", 1, new BigDecimal("99.90"));

    private final RestClient restClient;

    @Autowired
    public OrderClient(LoadGeneratorProperties properties) {
        this(RestClient.builder().requestFactory(createRequestFactory(properties.timeout())).build());
    }

    OrderClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Sends one order to the shop at the given base URL.
     * <p>
     * Never throws: an error status is a regular result, a timeout or a connection error is
     * reported as such.
     *
     * @param baseUrl the base URL of the shop, e.g. {@code http://localhost:8080}
     * @return the outcome, the status code (0 without a response) and the duration of the request
     */
    public OrderResult send(String baseUrl) {
        OrderOutcome outcome;
        int statusCode;

        long start = System.nanoTime();
        try {
            HttpStatusCode status = restClient.post()
                    .uri("%s/orders".formatted(baseUrl))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ORDER)
                    .exchange((_, response) -> response.getStatusCode());
            outcome = OrderOutcome.RESPONSE;
            statusCode = status.value();
        } catch (ResourceAccessException e) {
            outcome = e.getCause() instanceof HttpTimeoutException
                    ? OrderOutcome.TIMEOUT
                    : OrderOutcome.CONNECTION_ERROR;
            statusCode = 0;
        }
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        return new OrderResult(outcome, statusCode, duration);
    }

    private static JdkClientHttpRequestFactory createRequestFactory(Duration timeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }
}
