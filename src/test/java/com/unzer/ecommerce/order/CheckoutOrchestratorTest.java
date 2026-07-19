package com.unzer.ecommerce.order;

import com.unzer.ecommerce.catalog.Product;
import com.unzer.ecommerce.catalog.ProductRepository;
import com.unzer.ecommerce.catalog.ProductVariant;
import com.unzer.ecommerce.catalog.ProductVariantRepository;
import com.unzer.ecommerce.inventory.Stock;
import com.unzer.ecommerce.inventory.StockRepository;
import com.unzer.ecommerce.payment.PaymentAttempt;
import com.unzer.ecommerce.payment.PaymentAttemptRepository;
import com.unzer.ecommerce.payment.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class CheckoutOrchestratorTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private CheckoutOrchestrator checkoutOrchestrator;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private StockRepository stockRepository;

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
        stock.setQuantityTotal(5);
        stock.setQuantityReserved(0);
        stockRepository.save(stock);
    }

    @Test
    void checkout_creditCard_hasNoRedirectUrlAndRecordsUnzerIds() {
        String idempotencyKey = UUID.randomUUID().toString();
        List<CheckoutItemRequest> items = List.of(new CheckoutItemRequest(variantId, 1));

        CheckoutResult result = checkoutOrchestrator.checkout(idempotencyKey, null, items, PaymentMethod.CREDIT_CARD, "mock-token");

        assertEquals(OrderStatus.AWAITING_PAYMENT, result.order().getStatus());
        assertNull(result.redirectUrl());

        PaymentAttempt attempt = paymentAttemptRepository.findByOrderId(result.order().getId()).get(0);
        assertNotNull(attempt.getUnzerResourceId());
        assertNotNull(attempt.getUnzerTransactionId());
        assertTrue(attempt.getUnzerResourceId().startsWith("mock-resource-"));
        assertTrue(attempt.getUnzerTransactionId().startsWith("mock-pay-"));
    }

    @Test
    void checkout_wero_returnsRedirectUrlForCustomerToComplete() {
        String idempotencyKey = UUID.randomUUID().toString();
        List<CheckoutItemRequest> items = List.of(new CheckoutItemRequest(variantId, 1));

        CheckoutResult result = checkoutOrchestrator.checkout(idempotencyKey, null, items, PaymentMethod.WERO, null);

        assertEquals(OrderStatus.AWAITING_PAYMENT, result.order().getStatus());
        assertNotNull(result.redirectUrl());
        assertTrue(result.redirectUrl().startsWith("https://mock-unzer.local/redirect/"));
    }
}