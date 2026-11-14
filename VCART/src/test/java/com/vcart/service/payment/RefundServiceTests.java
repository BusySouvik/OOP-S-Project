package com.vcart.service.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RefundServiceTests {
    private final RefundRepository repository = mock(RefundRepository.class);
    private final TestProvider provider = new TestProvider();
    private final RefundService service = new RefundService(repository, provider);
    private final RefundRepository.RefundAttempt attempt = new RefundRepository.RefundAttempt(
        4L, 9L, 3L, "refund-key", new BigDecimal("125.00"), "INR",
        "PLACED", null, "MOCK");

    @Test
    void successfulRefundRemainsExplicitlySimulated() {
        provider.result = new PaymentProvider.ProviderResult(
            PaymentProvider.Outcome.SUCCEEDED, "mock-refund");
        when(repository.claimProcessing(7L, 4L)).thenReturn(attempt);
        when(repository.ownerStatus(7L, 4L)).thenReturn(Map.of(
            "status", "PROCESSED", "admin_note", "Development simulation completed."));

        var response = service.process(7L, 4L);

        assertEquals("PROCESSED", response.get("status"));
        assertEquals(false, response.get("realPayment"));
        verify(repository).complete(attempt, provider.result);
        verify(repository, never()).releaseProcessing(attempt);
    }

    @Test
    void pendingRefundIsNotMarkedAsSuccessful() {
        provider.result = new PaymentProvider.ProviderResult(
            PaymentProvider.Outcome.PENDING, "mock-refund");
        when(repository.claimProcessing(7L, 4L)).thenReturn(attempt);
        when(repository.ownerStatus(7L, 4L)).thenReturn(Map.of("status", "PROCESSING"));

        var response = service.process(7L, 4L);

        assertEquals("PROCESSING", response.get("status"));
        verify(repository).complete(attempt, provider.result);
    }

    @Test
    void unclaimedRefundDoesNotInvokeProviderAgain() {
        when(repository.claimProcessing(7L, 4L)).thenReturn(null);
        when(repository.ownerStatus(7L, 4L)).thenReturn(Map.of("status", "PROCESSED"));

        var response = service.process(7L, 4L);

        assertEquals("PROCESSED", response.get("status"));
        assertEquals(0, provider.refundCalls);
        verify(repository, never()).complete(any(), any());
    }

    private static final class TestProvider implements PaymentProvider {
        private ProviderResult result;
        private int refundCalls;

        @Override public String code() { return "MOCK"; }
        @Override public String displayName() { return "DEVELOPMENT MOCK — NO MONEY MOVED"; }
        @Override public ProviderResult charge(PaymentCommand command) { return result; }
        @Override public ProviderResult refund(RefundCommand command) {
            refundCalls++;
            return result;
        }
    }
}
