package com.unzer.ecommerce.order;

public enum OrderStatus {
    CREATED,
    AWAITING_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    CANCELLED,
    FULFILLING,
    SHIPPED,
    COMPLETED,
    REFUNDED
}