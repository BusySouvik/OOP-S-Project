package com.vcart.service.payment;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RefundService {
    private final RefundRepository repository;
    private final PaymentProvider provider;

    public RefundService(RefundRepository repository, PaymentProvider provider) {
        this.repository = repository;
        this.provider = provider;
    }

    public Map<String, Object> request(Long userId, Long orderId, String reason) {
        return repository.requestForCustomer(userId, orderId, reason);
    }

    public Map<String, Object> requestForOwner(Long ownerId, Long orderId, String reason) {
        return repository.requestForOwner(ownerId, orderId, reason);
    }

    public Map<String, Object> customerStatus(Long userId, Long orderId) {
        return repository.customerStatus(userId, orderId);
    }

    public List<Map<String, Object>> ownerList(Long ownerId) {
        return repository.ownerList(ownerId);
    }

    public Map<String, Object> review(Long ownerId, Long refundId, String status, String note) {
        return repository.review(ownerId, refundId, status, note);
    }

    public Map<String, Object> process(Long ownerId, Long refundId) {
        RefundRepository.RefundAttempt attempt = repository.claimProcessing(ownerId, refundId);
        if (attempt == null) {
            return response(repository.ownerStatus(ownerId, refundId));
        }

        PaymentProvider.ProviderResult result;
        try {
            result = Objects.requireNonNull(provider.refund(new PaymentProvider.RefundCommand(
                attempt.refundId(), attempt.orderId(), attempt.amount(),
                attempt.currency(), attempt.idempotencyKey()
            )), "Refund provider returned no result.");
        } catch (RuntimeException failure) {
            try {
                repository.releaseProcessing(attempt);
            } catch (RuntimeException releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            throw failure;
        }

        repository.complete(attempt, result);
        return response(repository.ownerStatus(ownerId, refundId));
    }

    private Map<String, Object> response(Map<String, Object> status) {
        Map<String, Object> body = new LinkedHashMap<>(status);
        String paymentProvider = Objects.toString(status.get("payment_provider"), "");
        body.put("provider", provider.code().equalsIgnoreCase(paymentProvider)
            ? provider.displayName() : "UNVERIFIED (" + paymentProvider + ")");
        body.put("realPayment", false);
        return body;
    }
}
