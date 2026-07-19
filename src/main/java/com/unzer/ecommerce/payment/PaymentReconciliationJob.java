package com.unzer.ecommerce.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentReconciliationJob {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentAttemptService paymentAttemptService;

    @Value("${unzer.reconciliation.stale-threshold-minutes:10}")
    private int staleThresholdMinutes;

    @Scheduled(fixedDelayString = "${unzer.reconciliation.interval-ms:60000}")
    public void reconcileStalePendingAttempts() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(staleThresholdMinutes);
        List<PaymentAttempt> staleAttempts =
            paymentAttemptRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold);

        for (PaymentAttempt attempt : staleAttempts) {
            if (StringUtils.hasText(attempt.getUnzerTransactionId())) {
                paymentAttemptService.processWebhook(attempt.getUnzerTransactionId());
            }
        }
    }
}