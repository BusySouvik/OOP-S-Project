package com.vcart.service.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTests {
    private final PaymentStore store = mock(PaymentStore.class);
    private final TestProvider provider = new TestProvider();
    private final PaymentService service = new PaymentService(store, provider);
    private final PaymentStore.PaymentAttempt attempt = new PaymentStore.PaymentAttempt(
        3L, 9L, new BigDecimal("125.00"), "INR", "stable-key", "PENDING", "MOCK");

    @BeforeEach
    void setUp() {
        provider.result = new PaymentProvider.ProviderResult(
            PaymentProvider.Outcome.SUCCEEDED, "mock-reference");
        when(store.claim(7L, 9L, "MOCK")).thenReturn(new PaymentStore.Claim(attempt, true));
    }

    @Test
    void recordsMockSuccessAsSimulationAndPassesStableIdempotencyKey() {
        when(store.status(7L, 9L)).thenReturn(new PaymentStore.PaymentStatus(
            "SUCCEEDED", "mock-reference", attempt.amount(), "INR", "MOCK"));

        var response = service.process(7L, 9L);

        assertEquals("SUCCEEDED", response.get("status"));
        assertEquals(false, response.get("realPayment"));
        assertTrue(response.get("message").toString().contains("No money was moved"));
        assertEquals("stable-key", provider.paymentCommand.idempotencyKey());
        verify(store).complete(attempt, provider.result);
    }

    @Test
    void recordsPendingAndFailedProviderOutcomes() {
        for (PaymentProvider.Outcome outcome : new PaymentProvider.Outcome[] {
            PaymentProvider.Outcome.PENDING, PaymentProvider.Outcome.FAILED
        }) {
            reset(store);
            provider.result = new PaymentProvider.ProviderResult(outcome, "mock-reference");
            when(store.claim(7L, 9L, "MOCK")).thenReturn(new PaymentStore.Claim(attempt, true));
            when(store.status(7L, 9L)).thenReturn(new PaymentStore.PaymentStatus(
                outcome.name(), "mock-reference", attempt.amount(), "INR", "MOCK"));

            var response = service.process(7L, 9L);

            assertEquals(outcome.name(), response.get("status"));
            verify(store).complete(attempt, provider.result);
        }
    }

    @Test
    void duplicateClaimDoesNotCallProviderAgain() {
        when(store.claim(7L, 9L, "MOCK")).thenReturn(new PaymentStore.Claim(attempt, false));
        when(store.status(7L, 9L)).thenReturn(new PaymentStore.PaymentStatus(
            "PROCESSING", null, attempt.amount(), "INR", "MOCK"));

        var response = service.process(7L, 9L);

        assertEquals("PROCESSING", response.get("status"));
        assertFalse(Boolean.TRUE.equals(response.get("realPayment")));
        assertEquals(0, provider.chargeCalls);
        verify(store, never()).complete(any(), any());
    }

    @Test
    void legacyPaymentIsNotDescribedAsAMockSimulation() {
        when(store.status(7L, 9L)).thenReturn(new PaymentStore.PaymentStatus(
            "SUCCEEDED", "old-reference", attempt.amount(), "INR", "LEGACY"));

        var response = service.status(7L, 9L);

        assertTrue(response.get("provider").toString().contains("UNVERIFIED"));
        assertTrue(response.get("message").toString().contains("not been verified"));
        assertFalse(response.get("message").toString().contains("Development simulation"));
        assertEquals(false, response.get("realPayment"));
    }

    private static final class TestProvider implements PaymentProvider {
        private ProviderResult result;
        private PaymentCommand paymentCommand;
        private int chargeCalls;

        @Override public String code() { return "MOCK"; }
        @Override public String displayName() { return "DEVELOPMENT MOCK — NO MONEY MOVED"; }
        @Override public ProviderResult charge(PaymentCommand command) {
            chargeCalls++;
            paymentCommand = command;
            return result;
        }
        @Override public ProviderResult refund(RefundCommand command) { return result; }
    }
}
