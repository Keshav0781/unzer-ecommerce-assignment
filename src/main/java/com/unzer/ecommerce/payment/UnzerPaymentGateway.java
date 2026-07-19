package com.unzer.ecommerce.payment;

import com.unzer.payment.BasePayment;
import com.unzer.payment.Charge;
import com.unzer.payment.Payment;
import com.unzer.payment.Unzer;
import com.unzer.payment.paymenttypes.Pis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.util.Currency;

// Real Unzer Java SDK implementation, verified directly against the actual
// downloaded jar (java-sdk-5.2.0) using javap, not from documentation guesses.
// Credit Card and Open Banking (via Pis, Payment Initiation Service, the
// real class matching the PDF's "bank-transfer/instant-transfer" description)
// are wired. Wero is stubbed: no Wero class exists in this SDK version,
// confirmed by inspecting the jar directly.
// This class has never been run against a real key; verify live at the
// interview once sandbox access exists (see Section 1 assumptions).
@Service
@ConditionalOnProperty(name = "unzer.payment-gateway", havingValue = "live")
public class UnzerPaymentGateway implements PaymentGateway {

    private final Unzer unzer;
    private final String returnUrl;

    public UnzerPaymentGateway(
        @Value("${unzer.api-key}") String apiKey,
        @Value("${unzer.return-url}") String returnUrl
    ) {
        this.unzer = new Unzer(apiKey);
        this.returnUrl = returnUrl;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        if (request.method() == PaymentMethod.WERO) {
            throw new UnsupportedOperationException(
                "Wero is designed but stubbed for this slice; no Wero class exists in java-sdk 5.2.0");
        }

        try {
            BigDecimal amount = BigDecimal.valueOf(request.amount()).movePointLeft(2);
            Currency currency = Currency.getInstance(request.currency());
            URL url = URI.create(returnUrl).toURL();

            Charge charge = switch (request.method()) {
                case CREDIT_CARD -> unzer.charge(amount, currency, request.paymentToken(), url);
                case OPEN_BANKING -> {
                    Pis pis = unzer.createPaymentType(new Pis());
                    yield unzer.charge(amount, currency, pis.getId(), url);
                }
                case WERO -> throw new IllegalStateException("unreachable, handled above");
            };

            String redirectUrl = charge.getRedirectUrl() != null ? charge.getRedirectUrl().toString() : null;
            return new ChargeResult(charge.getId(), charge.getPaymentId(), redirectUrl);
        } catch (Exception e) {
            throw new PaymentGatewayException("Unzer charge failed", e);
        }
    }

    @Override
    public PaymentStatusResult checkStatus(String paymentId) {
        try {
            Payment payment = unzer.fetchPayment(paymentId);
            BasePayment.State state = payment.getPaymentState();
            boolean succeeded = state == BasePayment.State.COMPLETED;
            boolean pending = state == BasePayment.State.PENDING;
            return new PaymentStatusResult(succeeded, pending);
        } catch (Exception e) {
            throw new PaymentGatewayException("Unzer status check failed", e);
        }
    }
}