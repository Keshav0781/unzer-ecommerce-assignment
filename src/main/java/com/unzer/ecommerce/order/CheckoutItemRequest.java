package com.unzer.ecommerce.order;

import java.util.UUID;

public record CheckoutItemRequest(UUID productVariantId, int quantity) {
}