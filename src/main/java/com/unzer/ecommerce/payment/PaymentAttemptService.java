package com.unzer.ecommerce.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentAttemptService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentGateway paymentGateway;

    @Transactional
    public PaymentAttempt createPendingAttempt(UUID orderId, PaymentMethod method) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setOrderId(orderId);
        attempt.setMethod(method);
        attempt.setStatus(PaymentStatus.PENDING);
        attempt.setIdempotencyKey(UUID.randomUUID().toString());
        attempt.setWebhookReceivedCount(0);
        attempt.setRetryCount(0);
        attempt.setCreatedAt(OffsetDateTime.now());
        attempt.setUpdatedAt(OffsetDateTime.now());
        return paymentAttemptRepository.save(attempt);
    }

    @Transactional
    public void recordChargeInitiated(UUID paymentAttemptId, String unzerResourceId, String unzerPaymentId) {
        PaymentAttempt attempt = paymentAttemptRepository.findById(paymentAttemptId)
            .orElseThrow(() -> new IllegalStateException("No payment attempt found: " + paymentAttemptId));

        attempt.setUnzerResourceId(unzerResourceId);
        attempt.setUnzerTransactionId(unzerPaymentId);
        attempt.setUpdatedAt(OffsetDateTime.now());
        paymentAttemptRepository.save(attempt);
    }

    @Transactional
    public void processWebhook(String unzerPaymentId) {
        PaymentAttempt attempt = paymentAttemptRepository.findByUnzerTransactionId(unzerPaymentId)
            .orElseThrow(() -> new IllegalStateException("No payment attempt found for Unzer payment: " + unzerPaymentId));

        attempt.setWebhookReceivedCount(attempt.getWebhookReceivedCount() + 1);

        if (attempt.getStatus() != PaymentStatus.PENDING) {
            paymentAttemptRepository.save(attempt);
            return;
        }

        PaymentStatusResult statusResult = paymentGateway.checkStatus(unzerPaymentId);

        if (statusResult.pending()) {
            paymentAttemptRepository.save(attempt);
            return;
        }

        attempt.setStatus(statusResult.succeeded() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED);
        attempt.setUpdatedAt(OffsetDateTime.now());
        paymentAttemptRepository.save(attempt);

        eventPublisher.publishEvent(new PaymentResultEvent(attempt.getOrderId(), attempt.getId(), statusResult.succeeded()));
    }
}