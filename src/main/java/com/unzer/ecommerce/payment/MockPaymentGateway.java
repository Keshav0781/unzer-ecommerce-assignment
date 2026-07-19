package com.unzer.ecommerce.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "unzer.payment-gateway", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private volatile boolean simulateFailure = false;
    private volatile boolean simulateChargeFailure = false;
    private volatile boolean simulateStatusCheckFailure = false;

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public void setSimulateChargeFailure(boolean simulateChargeFailure) {
        this.simulateChargeFailure = simulateChargeFailure;
    }

    public void setSimulateStatusCheckFailure(boolean simulateStatusCheckFailure) {
        this.simulateStatusCheckFailure = simulateStatusCheckFailure;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        if (simulateChargeFailure) {
            throw new PaymentGatewayException("Simulated Unzer charge failure", new RuntimeException("mock cause"));
        }

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
        if (simulateStatusCheckFailure) {
            throw new PaymentGatewayException("Simulated Unzer status check failure", new RuntimeException("mock cause"));
        }

        return new PaymentStatusResult(!simulateFailure, false);
    }
}