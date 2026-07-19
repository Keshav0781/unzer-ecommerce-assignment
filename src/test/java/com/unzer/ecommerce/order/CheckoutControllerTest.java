package com.unzer.ecommerce.order;

import com.unzer.ecommerce.catalog.Product;
import com.unzer.ecommerce.catalog.ProductRepository;
import com.unzer.ecommerce.catalog.ProductVariant;
import com.unzer.ecommerce.catalog.ProductVariantRepository;
import com.unzer.ecommerce.inventory.Stock;
import com.unzer.ecommerce.inventory.StockRepository;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CheckoutControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

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
    void checkout_creditCard_returnsAwaitingPaymentWithNoRedirect() throws Exception {
        String requestBody = """
            {
                "idempotencyKey": "%s",
                "customerId": null,
                "items": [{"productVariantId": "%s", "quantity": 1}],
                "method": "CREDIT_CARD",
                "paymentToken": "mock-token"
            }
            """.formatted(UUID.randomUUID(), variantId);

        mockMvc.perform(post("/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("AWAITING_PAYMENT"))
            .andExpect(jsonPath("$.redirectUrl").doesNotExist());
    }

    @Test
    void checkout_wero_returnsRedirectUrlInResponse() throws Exception {
        String requestBody = """
            {
                "idempotencyKey": "%s",
                "customerId": null,
                "items": [{"productVariantId": "%s", "quantity": 1}],
                "method": "WERO",
                "paymentToken": null
            }
            """.formatted(UUID.randomUUID(), variantId);

        mockMvc.perform(post("/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("AWAITING_PAYMENT"))
            .andExpect(jsonPath("$.redirectUrl").exists());
    }

    @Test
    void checkout_insufficientStock_returnsConflictNotServerError() throws Exception {
        String requestBody = """
            {
                "idempotencyKey": "%s",
                "customerId": null,
                "items": [{"productVariantId": "%s", "quantity": 100}],
                "method": "CREDIT_CARD",
                "paymentToken": "mock-token"
            }
            """.formatted(UUID.randomUUID(), variantId);

        mockMvc.perform(post("/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isConflict());
    }
}