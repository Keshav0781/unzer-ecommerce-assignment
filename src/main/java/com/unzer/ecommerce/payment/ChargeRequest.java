package com.unzer.ecommerce.payment;

import java.util.UUID;

public record ChargeRequest(UUID orderId, int amount, String currency, PaymentMethod method, String paymentToken) {
}