package com.unzer.ecommerce.order;

import com.unzer.ecommerce.payment.PaymentMethod;

import java.util.List;
import java.util.UUID;

public record CheckoutRequest(String idempotencyKey, UUID customerId, List<CheckoutItemRequest> items, PaymentMethod method, String paymentToken) {
}