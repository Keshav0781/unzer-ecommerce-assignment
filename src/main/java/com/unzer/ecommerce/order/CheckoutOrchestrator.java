package com.unzer.ecommerce.order;

import com.unzer.ecommerce.payment.ChargeRequest;
import com.unzer.ecommerce.payment.ChargeResult;
import com.unzer.ecommerce.payment.PaymentAttempt;
import com.unzer.ecommerce.payment.PaymentAttemptRepository;
import com.unzer.ecommerce.payment.PaymentAttemptService;
import com.unzer.ecommerce.payment.PaymentGateway;
import com.unzer.ecommerce.payment.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutOrchestrator {

    private final OrderService orderService;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentAttemptService paymentAttemptService;
    private final PaymentGateway paymentGateway;

    public CheckoutResult checkout(String idempotencyKey, UUID customerId, List<CheckoutItemRequest> items, PaymentMethod method, String paymentToken) {
        Order order = orderService.startCheckout(idempotencyKey, customerId, items, method);

        PaymentAttempt attempt = paymentAttemptRepository.findByOrderId(order.getId()).get(0);

        ChargeRequest chargeRequest = new ChargeRequest(order.getId(), order.getTotalAmount(), order.getTotalCurrency(), method, paymentToken);
        ChargeResult result = paymentGateway.charge(chargeRequest);

        paymentAttemptService.recordChargeInitiated(attempt.getId(), result.unzerResourceId(), result.unzerTransactionId());

        return new CheckoutResult(order, result.redirectUrl());
    }
}