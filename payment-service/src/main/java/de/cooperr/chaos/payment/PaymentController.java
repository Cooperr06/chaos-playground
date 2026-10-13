package de.cooperr.chaos.payment;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse pay(@Valid @RequestBody PaymentRequest request) {
        var response = new PaymentResponse(UUID.randomUUID(), request.orderId(), PaymentStatus.APPROVED);
        log.info("Payment approved: orderId={}, paymentId={}", response.orderId(), response.paymentId());
        return response;
    }
}
