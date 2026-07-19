package com.unzer.ecommerce.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentAttempt> findByUnzerTransactionId(String unzerTransactionId);

    List<PaymentAttempt> findByOrderId(UUID orderId);
}