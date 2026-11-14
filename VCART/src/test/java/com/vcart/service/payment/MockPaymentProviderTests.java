package com.vcart.service.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockPaymentProviderTests {
    @Test
    void configuredOutcomesProduceStableMockReferences() {
        MockPaymentProvider provider = new MockPaymentProvider("PENDING", "FAILED");

        var charge = provider.charge(new PaymentProvider.PaymentCommand(
            1L, new BigDecimal("45.00"), "INR", "payment-key"));
        var refund = provider.refund(new PaymentProvider.RefundCommand(
            2L, 1L, new BigDecimal("45.00"), "INR", "refund-key"));

        assertEquals(PaymentProvider.Outcome.PENDING, charge.outcome());
        assertEquals("MOCK-PAY-payment-key", charge.reference());
        assertEquals(PaymentProvider.Outcome.FAILED, refund.outcome());
        assertEquals("MOCK-REF-refund-key", refund.reference());
        assertEquals("MOCK", provider.code());
    }

    @Test
    void rejectsUnsupportedMockOutcomeConfiguration() {
        assertThrows(IllegalArgumentException.class,
            () -> new MockPaymentProvider("UNKNOWN", "SUCCEEDED"));
    }
}
