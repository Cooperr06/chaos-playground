package de.cooperr.chaos.payment.controller;

import de.cooperr.chaos.payment.dto.PaymentRequest;
import de.cooperr.chaos.payment.dto.PaymentResponse;
import de.cooperr.chaos.payment.dto.PaymentStatus;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse pay(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = new PaymentResponse(UUID.randomUUID(), request.orderId(), PaymentStatus.APPROVED);
        log.info("Payment approved: orderId={}, paymentId={}", response.orderId(), response.paymentId());
        return response;
    }
}
