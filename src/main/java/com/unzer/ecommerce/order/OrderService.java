package com.unzer.ecommerce.order;

import com.unzer.ecommerce.catalog.ProductVariant;
import com.unzer.ecommerce.catalog.ProductVariantRepository;
import com.unzer.ecommerce.inventory.InventoryService;
import com.unzer.ecommerce.payment.PaymentAttemptService;
import com.unzer.ecommerce.payment.PaymentMethod;
import com.unzer.ecommerce.payment.PaymentResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;
    private final ProductVariantRepository productVariantRepository;
    private final PaymentAttemptService paymentAttemptService;

    @Transactional
    public Order startCheckout(String idempotencyKey, UUID customerId, List<CheckoutItemRequest> items, PaymentMethod method) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }

        return orderRepository.findByIdempotencyKey(idempotencyKey)
            .orElseGet(() -> createOrder(idempotencyKey, customerId, items, method));
    }

    private Order createOrder(String idempotencyKey, UUID customerId, List<CheckoutItemRequest> items, PaymentMethod method) {
        for (CheckoutItemRequest item : items) {
            inventoryService.reserveStock(item.productVariantId(), item.quantity());
        }

        int totalAmount = 0;
        String currency = null;
        for (CheckoutItemRequest item : items) {
            ProductVariant variant = productVariantRepository.findById(item.productVariantId())
                .orElseThrow(() -> new IllegalStateException("No product variant found: " + item.productVariantId()));
            totalAmount += variant.getPriceAmount() * item.quantity();
            currency = variant.getPriceCurrency();
        }

        Order order = new Order();
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setIdempotencyKey(idempotencyKey);
        order.setTotalAmount(totalAmount);
        order.setTotalCurrency(currency);
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        order = orderRepository.save(order);

        for (CheckoutItemRequest item : items) {
            ProductVariant variant = productVariantRepository.findById(item.productVariantId())
                .orElseThrow(() -> new IllegalStateException("No product variant found: " + item.productVariantId()));

            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductVariantId(item.productVariantId());
            orderItem.setQuantity(item.quantity());
            orderItem.setPriceAtPurchase(variant.getPriceAmount());
            orderItemRepository.save(orderItem);
        }

        paymentAttemptService.createPendingAttempt(order.getId(), method);

        return order;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentResult(PaymentResultEvent event) {
        Order order = orderRepository.findById(event.orderId())
            .orElseThrow(() -> new IllegalStateException("No order found: " + event.orderId()));

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

        if (event.succeeded()) {
            for (OrderItem item : orderItems) {
                inventoryService.confirmStock(item.getProductVariantId(), item.getQuantity());
            }
            order.setStatus(OrderStatus.PAID);
        } else {
            for (OrderItem item : orderItems) {
                inventoryService.releaseStock(item.getProductVariantId(), item.getQuantity());
            }
            order.setStatus(OrderStatus.PAYMENT_FAILED);
        }

        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);
    }
}