package de.cooperr.chaos.loadgenerator.service;

import de.cooperr.chaos.loadgenerator.client.OrderClient;
import de.cooperr.chaos.loadgenerator.config.LoadGeneratorProperties;
import de.cooperr.chaos.loadgenerator.dto.LoadStatus;
import de.cooperr.chaos.loadgenerator.dto.OrderOutcome;
import de.cooperr.chaos.loadgenerator.dto.OrderResult;
import de.cooperr.chaos.loadgenerator.dto.TargetStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LoadServiceTest {

    private static final String URL = "http://shop-service";
    private static final OrderResult CREATED = new OrderResult(OrderOutcome.RESPONSE, 201, Duration.ofMillis(5));

    private OrderClient orderClient;
    private LoadService service;

    @BeforeEach
    void setUp() {
        orderClient = mock(OrderClient.class);
        when(orderClient.send(anyString())).thenReturn(CREATED);
        service = new LoadService(new LoadGeneratorProperties(List.of(URL), 0, Duration.ofSeconds(1)), orderClient);
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void updateRate_notCalled_doesNotSend() {
        verify(orderClient, after(300).never()).send(anyString());
    }

    @Test
    void updateRate_twenty_sendsAboutTwentyPerSecond() {
        service.updateRate(20);

        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(orderClient, atLeast(15)).send(URL));

        TargetStatus target = service.getStatus().targets().getFirst();
        assertTrue(target.statusCounts().get(201) >= 15);
    }

    @Test
    void updateRate_blockingClient_keepsRate() {
        when(orderClient.send(anyString())).thenAnswer(_ -> {
            try {
                Thread.sleep(2000);
                return CREATED;
            } catch (InterruptedException e) {
                // close() interrupts hanging requests, the real client reports them as connection errors
                return new OrderResult(OrderOutcome.CONNECTION_ERROR, 0, Duration.ZERO);
            }
        });

        service.updateRate(20);

        await().atMost(Duration.ofMillis(1500))
                .untilAsserted(() -> verify(orderClient, atLeast(15)).send(URL));
        assertTrue(service.getStatus().targets().getFirst().inFlight() > 0);
    }

    @Test
    void updateRate_zeroAfterTwenty_stopsSending() throws InterruptedException {
        service.updateRate(20);
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(orderClient, atLeast(5)).send(URL));

        service.updateRate(0);
        Thread.sleep(200);
        int callsAfterStop = mockingDetails(orderClient).getInvocations().size();
        Thread.sleep(300);

        assertEquals(callsAfterStop, mockingDetails(orderClient).getInvocations().size());
    }

    @Test
    void updateRate_newRate_resetsCounters() {
        service.updateRate(20);
        await().atMost(Duration.ofSeconds(2))
                .until(() -> service.getStatus().targets().getFirst().sent() >= 5);

        LoadStatus status = service.updateRate(10);

        assertEquals(10, status.ratePerSecond());
        assertEquals(0, status.targets().getFirst().sent());
    }
}
