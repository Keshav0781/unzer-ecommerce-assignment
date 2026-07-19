package com.unzer.ecommerce.order;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutOrchestrator checkoutOrchestrator;

    @PostMapping
    public ResponseEntity<CheckoutResult> checkout(@RequestBody CheckoutRequest request) {
        CheckoutResult result = checkoutOrchestrator.checkout(
            request.idempotencyKey(),
            request.customerId(),
            request.items(),
            request.method(),
            request.paymentToken()
        );
        return ResponseEntity.ok(result);
    }
}