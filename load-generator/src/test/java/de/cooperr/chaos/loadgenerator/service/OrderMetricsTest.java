package de.cooperr.chaos.loadgenerator.service;

import de.cooperr.chaos.loadgenerator.dto.OrderOutcome;
import de.cooperr.chaos.loadgenerator.dto.OrderResult;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderMetricsTest {

    private static final String URL = "http://shop-plain:8080";

    private SimpleMeterRegistry registry;
    private OrderMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new OrderMetrics(registry, List.of(URL));
    }

    @Test
    void record_response_countsByTargetAndStatus() {
        Duration duration = Duration.ofMillis(40);

        metrics.recordSent(URL);
        metrics.record(URL, new OrderResult(OrderOutcome.RESPONSE, 201, duration));

        Timer timer = registry.get(OrderMetrics.ORDERS).tag("target", "shop-plain").tag("result", "201").timer();
        assertEquals(1, timer.count());
        assertEquals(duration.toNanos(), (long) timer.totalTime(TimeUnit.NANOSECONDS));
    }

    @Test
    void record_timeout_countsAsTimeout() {
        metrics.recordSent(URL);
        metrics.record(URL, new OrderResult(OrderOutcome.TIMEOUT, 0, Duration.ofSeconds(10)));

        assertEquals(1, registry.get(OrderMetrics.ORDERS).tag("result", "timeout").timer().count());
    }

    @Test
    void record_connectionError_countsAsConnectionError() {
        metrics.recordSent(URL);
        metrics.record(URL, new OrderResult(OrderOutcome.CONNECTION_ERROR, 0, Duration.ZERO));

        assertEquals(1, registry.get(OrderMetrics.ORDERS).tag("result", "connection_error").timer().count());
    }

    @Test
    void recordSent_noResponseYet_countsInFlight() {
        metrics.recordSent(URL);
        metrics.recordSent(URL);
        metrics.record(URL, new OrderResult(OrderOutcome.RESPONSE, 201, Duration.ofMillis(5)));

        assertEquals(1, registry.get(OrderMetrics.IN_FLIGHT).tag("target", "shop-plain").gauge().value());
    }
}
