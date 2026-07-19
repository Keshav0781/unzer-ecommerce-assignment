package com.unzer.ecommerce.order;

import com.unzer.ecommerce.catalog.Product;
import com.unzer.ecommerce.catalog.ProductRepository;
import com.unzer.ecommerce.catalog.ProductVariant;
import com.unzer.ecommerce.catalog.ProductVariantRepository;
import com.unzer.ecommerce.inventory.InsufficientStockException;
import com.unzer.ecommerce.inventory.Stock;
import com.unzer.ecommerce.inventory.StockRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class OrderServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private StockRepository stockRepository;

    private UUID variantId;
    private UUID lowStockVariantId;

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

        ProductVariant lowStockVariant = new ProductVariant();
        lowStockVariant.setProductId(product.getId());
        lowStockVariant.setVariantLabel("LowStock");
        lowStockVariant.setPriceAmount(999);
        lowStockVariant.setPriceCurrency("EUR");
        lowStockVariant = productVariantRepository.save(lowStockVariant);
        lowStockVariantId = lowStockVariant.getId();

        Stock lowStock = new Stock();
        lowStock.setProductVariantId(lowStockVariantId);
        lowStock.setQuantityTotal(0);
        lowStock.setQuantityReserved(0);
        stockRepository.save(lowStock);
    }

    @Test
    void startCheckout_createsOrderAndReservesStockTogether() {
        String idempotencyKey = UUID.randomUUID().toString();
        List<CheckoutItemRequest> items = List.of(new CheckoutItemRequest(variantId, 2));

        Order order = orderService.startCheckout(idempotencyKey, null, items, PaymentMethod.CREDIT_CARD);

        assertEquals(OrderStatus.AWAITING_PAYMENT, order.getStatus());
        assertEquals(3998, order.getTotalAmount());
        assertEquals("EUR", order.getTotalCurrency());

        Stock stock = stockRepository.findByProductVariantId(variantId).orElseThrow();
        assertEquals(2, stock.getQuantityReserved());
    }

    @Test
    void startCheckout_isIdempotent() {
        String idempotencyKey = UUID.randomUUID().toString();
        List<CheckoutItemRequest> items = List.of(new CheckoutItemRequest(variantId, 1));

        Order firstAttempt = orderService.startCheckout(idempotencyKey, null, items, PaymentMethod.CREDIT_CARD);
        Order secondAttempt = orderService.startCheckout(idempotencyKey, null, items, PaymentMethod.CREDIT_CARD);

        assertEquals(firstAttempt.getId(), secondAttempt.getId());

        Stock stock = stockRepository.findByProductVariantId(variantId).orElseThrow();
        assertEquals(1, stock.getQuantityReserved());
        assertEquals(1, orderRepository.findAll().size());
    }

    @Test
    void startCheckout_rejectsBlankIdempotencyKey() {
        List<CheckoutItemRequest> items = List.of(new CheckoutItemRequest(variantId, 1));

        assertThrows(IllegalArgumentException.class, () ->
            orderService.startCheckout("", null, items, PaymentMethod.CREDIT_CARD));
        assertThrows(IllegalArgumentException.class, () ->
            orderService.startCheckout(null, null, items, PaymentMethod.CREDIT_CARD));
    }

    @Test
    void startCheckout_rejectsEmptyItems() {
        assertThrows(IllegalArgumentException.class, () ->
            orderService.startCheckout(UUID.randomUUID().toString(), null, List.of(), PaymentMethod.CREDIT_CARD));
    }

    @Test
    void startCheckout_rollsBackAllReservationsWhenOneItemFails() {
        String idempotencyKey = UUID.randomUUID().toString();
        List<CheckoutItemRequest> items = List.of(
            new CheckoutItemRequest(variantId, 1),
            new CheckoutItemRequest(lowStockVariantId, 1)
        );

        assertThrows(InsufficientStockException.class, () ->
            orderService.startCheckout(idempotencyKey, null, items, PaymentMethod.CREDIT_CARD));

        Stock stock = stockRepository.findByProductVariantId(variantId).orElseThrow();
        assertEquals(0, stock.getQuantityReserved());

        assertEquals(0, orderRepository.findAll().size());
    }
}