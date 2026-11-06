package de.cooperr.chaos.shop.service;

import de.cooperr.chaos.shop.client.InventoryClient;
import de.cooperr.chaos.shop.client.PaymentClient;
import de.cooperr.chaos.shop.config.ShopProperties;
import de.cooperr.chaos.shop.dto.OrderRequest;
import de.cooperr.chaos.shop.dto.OrderResponse;
import de.cooperr.chaos.shop.dto.OrderStatus;
import de.cooperr.chaos.shop.exception.DownstreamServiceException;
import de.cooperr.chaos.shop.exception.InsufficientStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private InventoryClient inventoryClient;
    private PaymentClient paymentClient;
    private OrderService orderService;

    private OrderService resilientOrderService;

    @BeforeEach
    void setUp() {
        inventoryClient = mock(InventoryClient.class);
        paymentClient = mock(PaymentClient.class);
        orderService = new OrderService(inventoryClient, paymentClient, properties(false));
        resilientOrderService = new OrderService(inventoryClient, paymentClient, properties(true));
    }

    private static ShopProperties properties(boolean resilienceEnabled) {
        return new ShopProperties("http://inventory-service", "http://payment-service",
                resilienceEnabled, Duration.ofSeconds(1));
    }

    @Test
    void placeOrder_validRequest_returnsConfirmedOrder() {
        String productId = "a";
        int quantity = 2;
        BigDecimal totalAmount = new BigDecimal("99.90");
        UUID reservationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        OrderRequest request = new OrderRequest(productId, quantity, totalAmount);

        when(inventoryClient.reserve(productId, quantity)).thenReturn(reservationId);
        when(paymentClient.pay(any(UUID.class), eq(totalAmount))).thenReturn(paymentId);

        OrderResponse response = orderService.placeOrder(request);

        assertAll(
                () -> assertNotNull(response.orderId()),
                () -> assertEquals(productId, response.productId()),
                () -> assertEquals(quantity, response.quantity()),
                () -> assertEquals(reservationId, response.reservationId()),
                () -> assertEquals(paymentId, response.paymentId()),
                () -> assertEquals(OrderStatus.CONFIRMED, response.status())
        );
        verify(paymentClient).pay(response.orderId(), totalAmount);
    }

    @Test
    void placeOrder_insufficientStock_throwsInsufficientStockExceptionAndSkipsPayment() {
        String productId = "a";
        int quantity = 60;
        OrderRequest request = new OrderRequest(productId, quantity, new BigDecimal("99.90"));

        when(inventoryClient.reserve(productId, quantity)).thenThrow(new InsufficientStockException(productId, quantity));

        assertThrows(InsufficientStockException.class, () -> orderService.placeOrder(request));
        verify(paymentClient, never()).pay(any(), any());
    }

    @Test
    void placeOrder_paymentFails_throwsDownstreamServiceException() {
        String productId = "a";
        int quantity = 2;
        OrderRequest request = new OrderRequest(productId, quantity, new BigDecimal("99.90"));

        when(inventoryClient.reserve(productId, quantity)).thenReturn(UUID.randomUUID());
        when(paymentClient.pay(any(), any()))
                .thenThrow(new DownstreamServiceException("payment-service", new RuntimeException("connection refused")));

        assertThrows(DownstreamServiceException.class, () -> orderService.placeOrder(request));
    }

    @Test
    void placeOrder_paymentFailsWithResilience_returnsPaymentPending() {
        String productId = "a";
        int quantity = 2;
        UUID reservationId = UUID.randomUUID();
        OrderRequest request = new OrderRequest(productId, quantity, new BigDecimal("99.90"));

        when(inventoryClient.reserve(productId, quantity)).thenReturn(reservationId);
        when(paymentClient.pay(any(), any()))
                .thenThrow(new DownstreamServiceException("payment-service", new RuntimeException("timeout")));

        OrderResponse response = resilientOrderService.placeOrder(request);

        assertAll(
                () -> assertEquals(OrderStatus.PAYMENT_PENDING, response.status()),
                () -> assertNull(response.paymentId()),
                () -> assertEquals(reservationId, response.reservationId())
        );
    }

    @Test
    void placeOrder_inventoryFailsWithResilience_throwsDownstreamServiceException() {
        String productId = "a";
        int quantity = 2;
        OrderRequest request = new OrderRequest(productId, quantity, new BigDecimal("99.90"));

        when(inventoryClient.reserve(productId, quantity))
                .thenThrow(new DownstreamServiceException("inventory-service", new RuntimeException("connection refused")));

        assertThrows(DownstreamServiceException.class, () -> resilientOrderService.placeOrder(request));
        verify(paymentClient, never()).pay(any(), any());
    }
}
