package com.vcart.service.payment;

import java.math.BigDecimal;

public interface PaymentProvider {
    String code();

    String displayName();

    ProviderResult charge(PaymentCommand command);

    ProviderResult refund(RefundCommand command);

    enum Outcome {
        SUCCEEDED,
        FAILED,
        PENDING
    }

    record PaymentCommand(
        Long orderId,
        BigDecimal amount,
        String currency,
        String idempotencyKey
    ) {}

    record RefundCommand(
        Long refundId,
        Long orderId,
        BigDecimal amount,
        String currency,
        String idempotencyKey
    ) {}

    record ProviderResult(Outcome outcome, String reference) {}
}
