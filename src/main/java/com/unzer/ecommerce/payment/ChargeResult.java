package com.unzer.ecommerce.payment;

public record ChargeResult(String unzerResourceId, String unzerTransactionId, String redirectUrl) {
}