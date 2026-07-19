package com.unzer.ecommerce.payment;

public interface PaymentGateway {

    ChargeResult charge(ChargeRequest request);

    PaymentStatusResult checkStatus(String paymentId);
}