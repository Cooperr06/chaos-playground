package de.cooperr.chaos.payment.service;

import de.cooperr.chaos.payment.dto.ChaosSettings;
import de.cooperr.chaos.payment.exception.ChaosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class ChaosServiceTest {

    private ChaosService service;

    @BeforeEach
    public void setUp() {
        service = new ChaosService();
    }

    @Test
    void getSettings_initial_returnsZero() {
        var expected = ChaosSettings.DEFAULT;
        var actual = service.getSettings();

        assertEquals(expected, actual);
    }

    @Test
    void updateSettings_twice_replacesPreviousSettings() {
        var settings = new ChaosSettings(200, 0.5);
        var newSettings = new ChaosSettings(500, 0.7);

        service.updateSettings(settings);
        service.updateSettings(newSettings);

        assertEquals(newSettings, service.getSettings());
    }

    @Test
    void injectChaos_errorRateZero_doesNotThrow() {
        var settings = new ChaosSettings(200, 0.0);

        service.updateSettings(settings);

        assertDoesNotThrow(() -> service.injectChaos());
    }

    @Test
    void injectChaos_errorRateOne_throwsChaosException() {
        var settings = new ChaosSettings(200, 1.0);

        service.updateSettings(settings);

        assertThrows(ChaosException.class, () -> service.injectChaos());
    }

    @Test
    void injectChaos_latencySet_waitsAtLeastLatency() {
        var settings = new ChaosSettings(200, 0.5);

        service.updateSettings(settings);

        // TODO
    }
}
