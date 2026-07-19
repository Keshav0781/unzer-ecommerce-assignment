package com.unzer.ecommerce.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentAttempt> findByUnzerTransactionId(String unzerTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentAttempt p WHERE p.unzerTransactionId = :unzerTransactionId")
    Optional<PaymentAttempt> findByUnzerTransactionIdForUpdate(@Param("unzerTransactionId") String unzerTransactionId);

    List<PaymentAttempt> findByOrderId(UUID orderId);

    List<PaymentAttempt> findByStatusAndCreatedAtBefore(PaymentStatus status, OffsetDateTime threshold);
}