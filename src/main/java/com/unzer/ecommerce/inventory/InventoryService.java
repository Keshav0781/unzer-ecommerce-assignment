package com.unzer.ecommerce.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final StockRepository stockRepository;

    @Transactional
    public void reserveStock(UUID variantId, int quantity) {
        requirePositiveQuantity(quantity);

        Stock stock = stockRepository.findByProductVariantIdForUpdate(variantId)
            .orElseThrow(() -> new IllegalStateException("No stock record found for variant: " + variantId));

        int available = stock.getQuantityTotal() - stock.getQuantityReserved();
        if (available < quantity) {
            throw new InsufficientStockException(variantId);
        }

        stock.setQuantityReserved(stock.getQuantityReserved() + quantity);
        stockRepository.save(stock);
    }

    @Transactional
    public void confirmStock(UUID variantId, int quantity) {
        requirePositiveQuantity(quantity);

        Stock stock = stockRepository.findByProductVariantIdForUpdate(variantId)
            .orElseThrow(() -> new IllegalStateException("No stock record found for variant: " + variantId));

        if (stock.getQuantityReserved() < quantity) {
            throw new IllegalStateException(
                "Cannot confirm " + quantity + " units, only " + stock.getQuantityReserved() + " reserved for variant: " + variantId);
        }

        stock.setQuantityTotal(stock.getQuantityTotal() - quantity);
        stock.setQuantityReserved(stock.getQuantityReserved() - quantity);
        stockRepository.save(stock);
    }

    @Transactional
    public void releaseStock(UUID variantId, int quantity) {
        requirePositiveQuantity(quantity);

        Stock stock = stockRepository.findByProductVariantIdForUpdate(variantId)
            .orElseThrow(() -> new IllegalStateException("No stock record found for variant: " + variantId));

        if (stock.getQuantityReserved() < quantity) {
            throw new IllegalStateException(
                "Cannot release " + quantity + " units, only " + stock.getQuantityReserved() + " reserved for variant: " + variantId);
        }

        stock.setQuantityReserved(stock.getQuantityReserved() - quantity);
        stockRepository.save(stock);
    }

    private void requirePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }
    }
}