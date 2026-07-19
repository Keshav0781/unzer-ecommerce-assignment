package com.unzer.ecommerce.payment;

import com.unzer.ecommerce.catalog.Product;
import com.unzer.ecommerce.catalog.ProductRepository;
import com.unzer.ecommerce.catalog.ProductVariant;
import com.unzer.ecommerce.catalog.ProductVariantRepository;
import com.unzer.ecommerce.inventory.Stock;
import com.unzer.ecommerce.inventory.StockRepository;
import com.unzer.ecommerce.order.CheckoutItemRequest;
import com.unzer.ecommerce.order.Order;
import com.unzer.ecommerce.order.OrderRepository;
import com.unzer.ecommerce.order.OrderService;
import com.unzer.ecommerce.order.OrderStatus;
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

@SpringBootTest
@Testcontainers
class PaymentAttemptServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentAttemptService paymentAttemptService;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private MockPaymentGateway mockPaymentGateway;

    private UUID variantId;

    @BeforeEach
    void setUp() {
        mockPaymentGateway.setSimulateFailure(false);

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
    void successfulWebhook_marksOrderPaidAndConfirmsStock() {
        mockPaymentGateway.setSimulateFailure(false);

        String idempotencyKey = UUID.randomUUID().toString();
        Order order = orderService.startCheckout(idempotencyKey, null, List.of(new CheckoutItemRequest(variantId, 2)), PaymentMethod.CREDIT_CARD);

        PaymentAttempt attempt = paymentAttemptRepository.findByOrderId(order.getId()).get(0);
        String unzerPaymentId = "test-pay-" + UUID.randomUUID();
        paymentAttemptService.recordChargeInitiated(attempt.getId(), "test-resource-id", unzerPaymentId);

        paymentAttemptService.processWebhook(unzerPaymentId);

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PAID, updatedOrder.getStatus());

        Stock stock = stockRepository.findByProductVariantId(variantId).orElseThrow();
        assertEquals(3, stock.getQuantityTotal());
        assertEquals(0, stock.getQuantityReserved());

        PaymentAttempt updatedAttempt = paymentAttemptRepository.findById(attempt.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, updatedAttempt.getStatus());
    }

    @Test
    void failedWebhook_marksOrderFailedAndReleasesStock() {
        mockPaymentGateway.setSimulateFailure(true);

        String idempotencyKey = UUID.randomUUID().toString();
        Order order = orderService.startCheckout(idempotencyKey, null, List.of(new CheckoutItemRequest(variantId, 2)), PaymentMethod.CREDIT_CARD);

        PaymentAttempt attempt = paymentAttemptRepository.findByOrderId(order.getId()).get(0);
        String unzerPaymentId = "test-pay-" + UUID.randomUUID();
        paymentAttemptService.recordChargeInitiated(attempt.getId(), "test-resource-id", unzerPaymentId);

        paymentAttemptService.processWebhook(unzerPaymentId);

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PAYMENT_FAILED, updatedOrder.getStatus());

        Stock stock = stockRepository.findByProductVariantId(variantId).orElseThrow();
        assertEquals(5, stock.getQuantityTotal());
        assertEquals(0, stock.getQuantityReserved());
    }

    @Test
    void duplicateWebhook_isIgnoredAfterFirstProcessing() {
        mockPaymentGateway.setSimulateFailure(false);

        String idempotencyKey = UUID.randomUUID().toString();
        Order order = orderService.startCheckout(idempotencyKey, null, List.of(new CheckoutItemRequest(variantId, 2)), PaymentMethod.CREDIT_CARD);

        PaymentAttempt attempt = paymentAttemptRepository.findByOrderId(order.getId()).get(0);
        String unzerPaymentId = "test-pay-" + UUID.randomUUID();
        paymentAttemptService.recordChargeInitiated(attempt.getId(), "test-resource-id", unzerPaymentId);

        paymentAttemptService.processWebhook(unzerPaymentId);
        paymentAttemptService.processWebhook(unzerPaymentId);

        Stock stock = stockRepository.findByProductVariantId(variantId).orElseThrow();
        assertEquals(3, stock.getQuantityTotal());

        PaymentAttempt updatedAttempt = paymentAttemptRepository.findById(attempt.getId()).orElseThrow();
        assertEquals(2, updatedAttempt.getWebhookReceivedCount());
        assertEquals(PaymentStatus.SUCCEEDED, updatedAttempt.getStatus());
    }
}