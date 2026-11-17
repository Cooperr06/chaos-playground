package de.cooperr.chaos.loadgenerator.service;

import de.cooperr.chaos.loadgenerator.dto.OrderResult;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Publishes the orders as Micrometer metrics, scraped by Prometheus.
 * <p>
 * Unlike {@link TargetCounter}, these meters are never reset: Prometheus works with ever-growing counters
 * and computes rates itself.
 */
class OrderMetrics {

    static final String ORDERS = "loadgenerator.orders";
    static final String IN_FLIGHT = "loadgenerator.orders.in.flight";

    private final MeterRegistry registry;
    private final Map<String, AtomicLong> inFlight;

    OrderMetrics(MeterRegistry registry, List<String> targetUrls) {
        this.registry = registry;
        this.inFlight = targetUrls.stream().collect(Collectors.toUnmodifiableMap(Function.identity(), _ -> new AtomicLong()));
        inFlight.forEach((url, count) -> Gauge.builder(IN_FLIGHT, count, AtomicLong::get)
                .description("Orders waiting for a response")
                .tag("target", target(url))
                .register(registry));
    }

    void recordSent(String url) {
        inFlight.get(url).incrementAndGet();
    }

    void record(String url, OrderResult result) {
        inFlight.get(url).decrementAndGet();
        Timer.builder(ORDERS)
                .description("Orders sent to a shop, by result")
                .tag("target", target(url))
                .tag("result", result(result))
                .publishPercentileHistogram()
                .register(registry)
                .record(result.duration());
    }

    /**
     * Returns the host of a target URL, e.g. {@code shop-plain} for {@code http://shop-plain:8080}.
     */
    static String target(String url) {
        return URI.create(url).getHost();
    }

    /**
     * Returns the status code, {@code timeout} or {@code connection_error}.
     */
    static String result(OrderResult result) {
        return switch (result.outcome()) {
            case RESPONSE -> String.valueOf(result.statusCode());
            case TIMEOUT -> "timeout";
            case CONNECTION_ERROR -> "connection_error";
        };
    }
}
