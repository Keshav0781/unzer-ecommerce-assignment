package com.unzer.ecommerce.payment;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class UnzerPaymentGatewayTest {

    @Test
    void charge_wero_throwsUnsupportedOperationException() {
        UnzerPaymentGateway gateway = new UnzerPaymentGateway("test-key", "https://example.com/return");

        ChargeRequest request = new ChargeRequest(UUID.randomUUID(), 1999, "EUR", PaymentMethod.WERO, null);

        assertThrows(UnsupportedOperationException.class, () -> gateway.charge(request));
    }
}