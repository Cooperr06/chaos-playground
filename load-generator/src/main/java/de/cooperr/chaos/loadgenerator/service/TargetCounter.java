package de.cooperr.chaos.loadgenerator.service;

import de.cooperr.chaos.loadgenerator.dto.OrderResult;
import de.cooperr.chaos.loadgenerator.dto.TargetStatus;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counts the requests sent to one target and their results.
 * <p>
 * Thread-safe without locks: many virtual threads record results at the same time.
 */
class TargetCounter {

    private final String url;

    private final LongAdder sent = new LongAdder();
    private final LongAdder inFlight = new LongAdder();
    private final Map<Integer, LongAdder> statusCounts = new ConcurrentHashMap<>();
    private final LongAdder timeouts = new LongAdder();
    private final LongAdder connectionErrors = new LongAdder();
    private final LongAdder totalDurationNanos = new LongAdder();
    private final LongAccumulator maxDurationNanos = new LongAccumulator(Math::max, 0);

    TargetCounter(String url) {
        this.url = url;
    }

    String url() {
        return url;
    }

    void recordSent() {
        sent.increment();
        inFlight.increment();
    }

    void record(OrderResult result) {
        inFlight.decrement();
        switch (result.outcome()) {
            case RESPONSE -> {
                statusCounts.computeIfAbsent(result.statusCode(), _ -> new LongAdder()).increment();
                long durationNanos = result.duration().toNanos();
                totalDurationNanos.add(durationNanos);
                maxDurationNanos.accumulate(durationNanos);
            }
            case TIMEOUT -> timeouts.increment();
            case CONNECTION_ERROR -> connectionErrors.increment();
        }
    }

    TargetStatus toStatus() {
        Map<Integer, Long> counts = new TreeMap<>();
        statusCounts.forEach((status, count) -> counts.put(status, count.sum()));

        long responses = counts.values().stream().mapToLong(Long::longValue).sum();
        long averageDurationNanos = responses == 0 ? 0 : totalDurationNanos.sum() / responses;

        return new TargetStatus(url,
                sent.sum(), inFlight.sum(),
                Collections.unmodifiableMap(counts),
                timeouts.sum(), connectionErrors.sum(),
                Duration.ofNanos(averageDurationNanos).toMillis(),
                Duration.ofNanos(maxDurationNanos.get()).toMillis());
    }
}
