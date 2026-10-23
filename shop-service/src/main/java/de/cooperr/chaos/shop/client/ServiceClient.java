package de.cooperr.chaos.shop.client;

import de.cooperr.chaos.shop.exception.DownstreamServiceException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

/**
 * Base class for clients that call another service of the shop over HTTP.
 * <p>
 * It owns the {@link RestClient} for the target service and translates technical failures
 * (connection errors, error statuses, empty responses) into a {@link DownstreamServiceException},
 * so callers only deal with the shop's own exceptions.
 */
abstract class ServiceClient {

    private final String service;

    protected final RestClient restClient;

    /**
     * Creates a client for one downstream service.
     *
     * @param service the name of the downstream service, used in error messages and logs
     * @param baseUrl the base URL of the downstream service, e.g. {@code http://localhost:8081}
     */
    protected ServiceClient(String service, String baseUrl) {
        this(service, RestClient.builder().baseUrl(baseUrl).build());
    }

    /**
     * Creates a client for one downstream service with a prepared {@link RestClient}, e.g. one bound
     * to a mock server in tests.
     *
     * @param service    the name of the downstream service, used in error messages and logs
     * @param restClient the client to use for all requests to the downstream service
     */
    protected ServiceClient(String service, RestClient restClient) {
        this.service = service;
        this.restClient = restClient;
    }

    /**
     * Executes a request against the downstream service.
     * <p>
     * A {@link RestClientException} (connection error or error status) and an empty response are
     * translated into a {@link DownstreamServiceException}. Other exceptions, such as those thrown by
     * {@code onStatus} handlers, are passed on unchanged.
     *
     * @param request the request to execute, returning the response body
     * @param <T>     the type of the response body
     * @return the response body, never {@code null}
     * @throws DownstreamServiceException if the request fails or the response is empty
     */
    protected <T> T call(Supplier<T> request) {
        try {
            T response = request.get();
            if (response == null) {
                throw new DownstreamServiceException(service, new IllegalStateException("Empty response"));
            }
            return response;
        } catch (RestClientException e) {
            throw new DownstreamServiceException(service, e);
        }
    }
}
