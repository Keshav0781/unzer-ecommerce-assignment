package com.unzer.ecommerce.order;

public record CheckoutResult(Order order, String redirectUrl) {
}