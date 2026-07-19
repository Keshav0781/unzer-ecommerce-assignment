package com.unzer.ecommerce.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/unzer")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentAttemptService paymentAttemptService;

    @PostMapping
    public ResponseEntity<Void> receiveWebhook(@RequestBody UnzerWebhookPayload payload) {
        try {
            paymentAttemptService.processWebhook(payload.paymentId());
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }
}