package de.cooperr.chaos.payment.service;

import de.cooperr.chaos.payment.dto.ChaosSettings;
import de.cooperr.chaos.payment.exception.ChaosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ChaosServiceTest {

    private ChaosService service;

    @BeforeEach
    void setUp() {
        service = new ChaosService();
    }

    @Test
    void getSettings_initial_returnsZero() {
        ChaosSettings expected = ChaosSettings.DEFAULT;
        ChaosSettings actual = service.getSettings();

        assertEquals(expected, actual);
    }

    @Test
    void updateSettings_twice_replacesPreviousSettings() {
        ChaosSettings settings = new ChaosSettings(200, 0.5);
        ChaosSettings newSettings = new ChaosSettings(500, 0.7);

        service.updateSettings(settings);
        service.updateSettings(newSettings);

        assertEquals(newSettings, service.getSettings());
    }

    @Test
    void injectChaos_errorRateZero_doesNotThrow() {
        ChaosSettings settings = new ChaosSettings(200, 0.0);

        service.updateSettings(settings);

        assertDoesNotThrow(() -> service.injectChaos());
    }

    @Test
    void injectChaos_errorRateOne_throwsChaosException() {
        ChaosSettings settings = new ChaosSettings(200, 1.0);

        service.updateSettings(settings);

        assertThrows(ChaosException.class, () -> service.injectChaos());
    }

    @Test
    void injectChaos_latencySet_waitsAtLeastLatency() {
        ChaosSettings settings = new ChaosSettings(200, 0.0);

        service.updateSettings(settings);

        long startTime = System.nanoTime();
        service.injectChaos();
        long endTime = System.nanoTime();

        long elapsedMs = Duration.ofNanos(endTime - startTime).toMillis();
        assertTrue(elapsedMs >= settings.latencyMs());
    }
}
