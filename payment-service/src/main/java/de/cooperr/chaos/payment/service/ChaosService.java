package de.cooperr.chaos.payment.service;

import de.cooperr.chaos.payment.dto.ChaosSettings;
import de.cooperr.chaos.payment.exception.ChaosException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ChaosService {

    private AtomicReference<ChaosSettings> settings = new AtomicReference<>(ChaosSettings.DEFAULT);

    public ChaosSettings getSettings() {
        return settings.get();
    }

    public ChaosSettings updateSettings(ChaosSettings newSettings) {
        return settings.updateAndGet(settings -> newSettings);
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
