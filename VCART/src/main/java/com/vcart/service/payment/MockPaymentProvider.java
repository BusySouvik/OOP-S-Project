package com.vcart.service.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

@Component
public class MockPaymentProvider implements PaymentProvider {
    private final Outcome chargeOutcome;
    private final Outcome refundOutcome;

    public MockPaymentProvider(
        @Value("${vcart.payment.mock.charge-outcome:SUCCEEDED}") String chargeOutcome,
        @Value("${vcart.payment.mock.refund-outcome:SUCCEEDED}") String refundOutcome
    ) {
        this.chargeOutcome = parseOutcome(chargeOutcome, "vcart.payment.mock.charge-outcome");
        this.refundOutcome = parseOutcome(refundOutcome, "vcart.payment.mock.refund-outcome");
    }

    @Override
    public String code() {
        return "MOCK";
    }

    @Override
    public String displayName() {
        return "DEVELOPMENT MOCK — NO MONEY MOVED";
    }

    @Override
    public ProviderResult charge(PaymentCommand command) {
        Objects.requireNonNull(command, "Payment command is required.");
        requireIdempotencyKey(command.idempotencyKey());
        return new ProviderResult(chargeOutcome, "MOCK-PAY-" + command.idempotencyKey());
    }

    @Override
    public ProviderResult refund(RefundCommand command) {
        Objects.requireNonNull(command, "Refund command is required.");
        requireIdempotencyKey(command.idempotencyKey());
        return new ProviderResult(refundOutcome, "MOCK-REF-" + command.idempotencyKey());
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("A provider idempotency key is required.");
        }
    }

    private static Outcome parseOutcome(String value, String property) {
        try {
            return Outcome.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                property + " must be SUCCEEDED, FAILED or PENDING.", ex);
        }
    }
}
