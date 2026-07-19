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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WebhookControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private PaymentAttemptService paymentAttemptService;

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
    void receiveWebhook_forKnownPayment_returnsOkAndUpdatesOrder() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        Order order = orderService.startCheckout(idempotencyKey, null, List.of(new CheckoutItemRequest(variantId, 1)), PaymentMethod.CREDIT_CARD);

        PaymentAttempt attempt = paymentAttemptRepository.findByOrderId(order.getId()).get(0);
        String unzerPaymentId = "test-pay-" + UUID.randomUUID();
        paymentAttemptService.recordChargeInitiated(attempt.getId(), "test-resource-id", unzerPaymentId);

        String requestBody = """
            {
                "event": "payment.completed",
                "publicKey": "s-pub-test",
                "paymentId": "%s",
                "retrieveUrl": "https://api.unzer.com/v1/payments/%s"
            }
            """.formatted(unzerPaymentId, unzerPaymentId);

        mockMvc.perform(post("/webhooks/unzer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk());

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        if (updatedOrder.getStatus() != OrderStatus.PAID) {
            throw new AssertionError("Expected order status PAID but was " + updatedOrder.getStatus());
        }
    }

    @Test
    void receiveWebhook_forUnknownPayment_returnsNotFound() throws Exception {
        String requestBody = """
            {
                "event": "payment.completed",
                "publicKey": "s-pub-test",
                "paymentId": "unknown-payment-id",
                "retrieveUrl": "https://api.unzer.com/v1/payments/unknown-payment-id"
            }
            """;

        mockMvc.perform(post("/webhooks/unzer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isNotFound());
    }
}