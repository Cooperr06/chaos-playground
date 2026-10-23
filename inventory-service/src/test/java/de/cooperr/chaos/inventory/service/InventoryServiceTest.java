package de.cooperr.chaos.inventory.service;

import de.cooperr.chaos.inventory.config.InventoryProperties;
import de.cooperr.chaos.inventory.dto.Reservation;
import de.cooperr.chaos.inventory.exception.InsufficientStockException;
import de.cooperr.chaos.inventory.exception.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InventoryServiceTest {

    private static final int STOCK_A = 5;
    private static final int STOCK_B = 50;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        InventoryProperties inventory = new InventoryProperties(Map.of("a", STOCK_A, "b", STOCK_B));
        inventoryService = new InventoryService(inventory);
    }

    @Test
    void getStock_existingProduct_returnsStock() {
        String productId = "a";

        assertEquals(STOCK_A, inventoryService.getStock(productId));
    }

    @Test
    void getStock_nonExistingProduct_throwsProductNotFoundException() {
        String nonExistingProduct = "nonExistingProduct";

        assertThrows(ProductNotFoundException.class, () -> inventoryService.getStock(nonExistingProduct));
    }

    @Test
    void reserve_sufficientStock_returnsReservation() {
        String productId = "a";
        int quantity = 2;

        Reservation reservation = inventoryService.reserve(productId, quantity);

        assertAll(
                () -> assertNotNull(reservation.reservationId()),
                () -> assertEquals(productId, reservation.productId()),
                () -> assertEquals(quantity, reservation.quantity()),
                () -> assertEquals(STOCK_A - quantity, inventoryService.getStock(productId))
        );
    }

    @Test
    void reserve_exactlySufficientStock_returnsReservation() {
        String productId = "a";
        int quantity = STOCK_A;

        Reservation reservation = inventoryService.reserve(productId, quantity);

        assertAll(
                () -> assertNotNull(reservation.reservationId()),
                () -> assertEquals(productId, reservation.productId()),
                () -> assertEquals(quantity, reservation.quantity()),
                () -> assertEquals(0, inventoryService.getStock(productId))
        );
    }

    @Test
    void reserve_zeroQuantity_throwsIllegalArgumentException() {
        String productId = "a";
        int quantity = 0;

        assertThrows(IllegalArgumentException.class, () -> inventoryService.reserve(productId, quantity));
    }

    @Test
    void reserve_nonExistingProduct_throwsProductNotFoundException() {
        String nonExistingProduct = "nonExistingProduct";
        int quantity = 2;

        assertThrows(ProductNotFoundException.class, () -> inventoryService.reserve(nonExistingProduct, quantity));
    }

    @Test
    void reserve_insufficientStock_throwsInsufficientStockException() {
        String productId = "a";
        int quantity = 7;

        assertAll(
                () -> assertThrows(InsufficientStockException.class, () -> inventoryService.reserve(productId, quantity)),
                () -> assertEquals(STOCK_A, inventoryService.getStock(productId))
        );
    }

    @RepeatedTest(10)
    void reserve_fiftyProductStockOneHundredAsyncReservations_returnsFiftyReservations() throws InterruptedException {
        String productId = "b";
        int quantity = 1;
        int threads = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        ConcurrentLinkedQueue<RuntimeException> exceptions = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    inventoryService.reserve(productId, quantity);
                    successes.incrementAndGet();
                } catch (InsufficientStockException e) {
                    failures.incrementAndGet();
                } catch (RuntimeException e) {
                    exceptions.add(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();

        try {
            assertTrue(done.await(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertAll(
                () -> assertTrue(exceptions.isEmpty()),
                () -> assertEquals(STOCK_B, successes.get()),
                () -> assertEquals(threads - STOCK_B, failures.get()),
                () -> assertEquals(0, inventoryService.getStock(productId))
        );
    }
}
