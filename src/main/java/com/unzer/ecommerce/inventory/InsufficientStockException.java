package com.unzer.ecommerce.inventory;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(UUID variantId) {
        super("Insufficient stock for product variant: " + variantId);
    }
}