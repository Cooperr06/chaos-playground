package de.cooperr.chaos.shop.controller;

import de.cooperr.chaos.shop.dto.OrderRequest;
import de.cooperr.chaos.shop.dto.OrderResponse;
import de.cooperr.chaos.shop.dto.OrderStatus;
import de.cooperr.chaos.shop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest orderRequest) {
        OrderResponse response = orderService.placeOrder(orderRequest);
        HttpStatus status = response.status() == OrderStatus.PAYMENT_PENDING ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }
}
