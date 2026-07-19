package com.unzer.ecommerce.inventory;

import com.unzer.ecommerce.catalog.Product;
import com.unzer.ecommerce.catalog.ProductRepository;
import com.unzer.ecommerce.catalog.ProductVariant;
import com.unzer.ecommerce.catalog.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class InventoryServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    private UUID variantId;

    @BeforeEach
    void setUp() {
        Product product = new Product();
        product.setName("Test Product");
        product.setDescription("A product created for tests");
        product.setCategory("test-category");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setProductId(product.getId());
        variant.setVariantLabel("Default");
        variant.setPriceAmount(1999);
        variant.setPriceCurrency("EUR");
        variant = productVariantRepository.save(variant);
        variantId = variant.getId();

        Stock stock = new Stock();
        stock.setProductVariantId(variantId);
        stock.setQuantityTotal(1);
        stock.setQuantityReserved(0);
        stockRepository.save(stock);
    }

    @Test
    void reserveStock_succeedsWhenStockAvailable() {
        inventoryService.reserveStock(variantId, 1);

        Stock stock = stockRepository.findByProductVariantId(variantId).orElseThrow();
        assertEquals(1, stock.getQuantityReserved());
    }

    @Test
    void reserveStock_throwsWhenInsufficientStock() {
        inventoryService.reserveStock(variantId, 1);

        assertThrows(InsufficientStockException.class, () ->
            inventoryService.reserveStock(variantId, 1));
    }

    @Test
    void reserveStock_rejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () ->
            inventoryService.reserveStock(variantId, 0));
        assertThrows(IllegalArgumentException.class, () ->
            inventoryService.reserveStock(variantId, -1));
    }

    @Test
    void confirmStock_reducesTotalAndReserved() {
        inventoryService.reserveStock(variantId, 1);
        inventoryService.confirmStock(variantId, 1);

        Stock stock = stockRepository.findByProductVariantId(variantId).orElseThrow();
        assertEquals(0, stock.getQuantityTotal());
        assertEquals(0, stock.getQuantityReserved());
    }

    @Test
    void releaseStock_reducesReservedOnly() {
        inventoryService.reserveStock(variantId, 1);
        inventoryService.releaseStock(variantId, 1);

        Stock stock = stockRepository.findByProductVariantId(variantId).orElseThrow();
        assertEquals(1, stock.getQuantityTotal());
        assertEquals(0, stock.getQuantityReserved());
    }

    @Test
    void reserveStock_preventsOversellUnderConcurrency() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    inventoryService.reserveStock(variantId, 1);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    failureCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(1, failureCount.get());

        Stock stock = stockRepository.findByProductVariantId(variantId).orElseThrow();
        assertEquals(1, stock.getQuantityReserved());
    }
}