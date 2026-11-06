package de.cooperr.chaos.loadgenerator.service;

import de.cooperr.chaos.loadgenerator.client.OrderClient;
import de.cooperr.chaos.loadgenerator.config.LoadGeneratorProperties;
import de.cooperr.chaos.loadgenerator.dto.LoadStatus;
import de.cooperr.chaos.loadgenerator.dto.OrderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Sends orders to all targets at a fixed rate.
 * <p>
 * One scheduler thread keeps the beat and never waits for a response: each request runs on its
 * own virtual thread. A slow or hanging shop therefore does not lower the rate.
 */
@Service
public class LoadService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LoadService.class);

    private final List<String> targetUrls;
    private final OrderClient orderClient;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService senders = Executors.newVirtualThreadPerTaskExecutor();

    private volatile int ratePerSecond;
    private volatile List<TargetCounter> counters;
    private ScheduledFuture<?> beat;

    public LoadService(LoadGeneratorProperties properties, OrderClient orderClient) {
        this.targetUrls = List.copyOf(properties.targetUrls());
        this.orderClient = orderClient;
        updateRate(properties.ratePerSecond());
    }

    public LoadStatus getStatus() {
        return toStatus(ratePerSecond, counters);
    }

    /**
     * Replaces the rate and starts counting from zero.
     * <p>
     * Requests started before the change are recorded in the discarded counters.
     *
     * @param ratePerSecond requests per second and target, 0 stops sending
     * @return the state right after the change
     */
    public synchronized LoadStatus updateRate(int ratePerSecond) {
        if (beat != null) {
            beat.cancel(false);
            beat = null;
        }

        List<TargetCounter> newCounters = targetUrls.stream().map(TargetCounter::new).toList();
        this.ratePerSecond = ratePerSecond;
        this.counters = newCounters;
        LoadStatus status = toStatus(ratePerSecond, newCounters);

        if (ratePerSecond > 0) {
            long periodMicros = TimeUnit.SECONDS.toMicros(1) / ratePerSecond;
            beat = scheduler.scheduleAtFixedRate(() -> sendToAll(newCounters),
                    0, periodMicros, TimeUnit.MICROSECONDS);
        }

        log.info("Load rate updated: ratePerSecond={}, targets={}", ratePerSecond, targetUrls);
        return status;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        senders.shutdownNow();
    }

    private void sendToAll(List<TargetCounter> counters) {
        for (TargetCounter counter : counters) {
            counter.recordSent();
            senders.execute(() -> {
                OrderResult result = orderClient.send(counter.url());
                counter.record(result);
                log.debug("Order sent: url={}, result={}", counter.url(), result);
            });
        }
    }

    private static LoadStatus toStatus(int ratePerSecond, List<TargetCounter> counters) {
        return new LoadStatus(ratePerSecond, counters.stream().map(TargetCounter::toStatus).toList());
    }
}
