package com.unzer.ecommerce.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "unzer.payment-gateway", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private volatile boolean simulateFailure = false;

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String resourceId = "mock-resource-" + UUID.randomUUID();
        String paymentId = "mock-pay-" + UUID.randomUUID();

        String redirectUrl = switch (request.method()) {
            case WERO, OPEN_BANKING -> "https://mock-unzer.local/redirect/" + paymentId;
            case CREDIT_CARD -> null;
        };

        return new ChargeResult(resourceId, paymentId, redirectUrl);
    }

    @Override
    public PaymentStatusResult checkStatus(String paymentId) {
        return new PaymentStatusResult(!simulateFailure, false);
    }
}