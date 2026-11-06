package de.cooperr.chaos.payment.service;

import de.cooperr.chaos.payment.dto.ChaosSettings;
import de.cooperr.chaos.payment.exception.ChaosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ChaosService {

    private static final Logger log = LoggerFactory.getLogger(ChaosService.class);

    private final AtomicReference<ChaosSettings> settings = new AtomicReference<>(ChaosSettings.DEFAULT);

    public ChaosSettings getSettings() {
        return settings.get();
    }

    public ChaosSettings updateSettings(ChaosSettings settings) {
        ChaosSettings updatedSettings = this.settings.updateAndGet(_ -> settings);
        log.info("Chaos settings updated: latencyMs={}, errorRate={}",
                updatedSettings.latencyMs(), updatedSettings.errorRate());
        return updatedSettings;
    }

    public void injectChaos() {
        try {
            Thread.sleep(settings.get().latencyMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (ThreadLocalRandom.current().nextDouble() < settings.get().errorRate()) {
            throw new ChaosException();
        }
    }
}
