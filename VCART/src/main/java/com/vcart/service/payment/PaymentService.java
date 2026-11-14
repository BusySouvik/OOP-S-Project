package com.vcart.service.payment;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class PaymentService {
    private final PaymentStore store;
    private final PaymentProvider provider;

    public PaymentService(PaymentStore store, PaymentProvider provider) {
        this.store = store;
        this.provider = provider;
    }

    public Map<String, Object> status(Long userId, Long orderId) {
        return response(store.status(userId, orderId));
    }

    public Map<String, Object> process(Long userId, Long orderId) {
        PaymentStore.Claim claim = store.claim(userId, orderId, provider.code());
        if (!claim.claimed()) {
            return response(store.status(userId, orderId));
        }

        PaymentStore.PaymentAttempt attempt = claim.attempt();
        PaymentProvider.ProviderResult result;
        try {
            result = Objects.requireNonNull(provider.charge(new PaymentProvider.PaymentCommand(
                attempt.orderId(), attempt.amount(), attempt.currency(), attempt.idempotencyKey()
            )), "Payment provider returned no result.");
        } catch (RuntimeException failure) {
            releaseClaim(attempt, failure);
            throw failure;
        }

        store.complete(attempt, result);
        return response(store.status(userId, orderId));
    }

    private void releaseClaim(PaymentStore.PaymentAttempt attempt, RuntimeException failure) {
        try {
            store.releaseClaim(attempt);
        } catch (RuntimeException releaseFailure) {
            failure.addSuppressed(releaseFailure);
        }
    }

    private Map<String, Object> response(PaymentStore.PaymentStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.status());
        body.put("amount", status.amount());
        body.put("currency", status.currency());
        boolean mock = provider.code().equalsIgnoreCase(status.provider());
        body.put("provider", mock ? provider.displayName()
            : "UNVERIFIED (" + status.provider() + ")");
        body.put("realPayment", false);
        body.put("message", mock ? switch (status.status()) {
                case "SUCCEEDED" -> "Development simulation succeeded. No money was moved.";
                case "FAILED" -> "Development simulation failed. No money was moved.";
                case "PROCESSING" -> "Payment simulation is still processing. No money was moved.";
                default -> "Development payment simulation is pending. No money was moved.";
            } : "This provider is not configured here; its payment status has not been verified.");
        if (status.providerReference() != null) {
            body.put("providerReference", status.providerReference());
        }
        return body;
    }
}
