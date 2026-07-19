package com.unzer.ecommerce.payment;

import java.util.UUID;

public record PaymentResultEvent(UUID orderId, UUID paymentAttemptId, boolean succeeded) {
}