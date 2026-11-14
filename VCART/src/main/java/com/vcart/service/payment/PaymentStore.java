package com.vcart.service.payment;

import com.vcart.service.OrderInventoryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class PaymentStore {
    private final JdbcTemplate jdbc;
    private final OrderInventoryService inventory;

    public PaymentStore(JdbcTemplate jdbc, OrderInventoryService inventory) {
        this.jdbc = jdbc;
        this.inventory = inventory;
    }

    @Transactional
    public void createPending(Long orderId, BigDecimal amount, String provider) {
        jdbc.update("""
            INSERT INTO payments(order_id, provider, idempotency_key, amount, currency, status)
            VALUES (?, ?, ?, ?, 'INR', 'PENDING')
            """, orderId, provider, UUID.randomUUID().toString(), amount);
    }

    @Transactional
    public Claim claim(Long userId, Long orderId, String expectedProvider) {
        List<String> orderStatuses = jdbc.query("""
            SELECT order_status FROM orders
            WHERE id = ? AND user_id = ?
            FOR UPDATE
            """, (rs, row) -> rs.getString("order_status"), orderId, userId);
        if (orderStatuses.isEmpty()) {
            throw new IllegalArgumentException("Order not found.");
        }
        if ("CANCELLED".equalsIgnoreCase(orderStatuses.get(0))) {
            throw new IllegalArgumentException("Cancelled orders cannot be paid.");
        }

        List<PaymentAttempt> payments = jdbc.query("""
            SELECT id, order_id, amount, currency, idempotency_key, status, provider
            FROM payments WHERE order_id = ? FOR UPDATE
            """, (rs, row) -> new PaymentAttempt(
                rs.getLong("id"),
                rs.getLong("order_id"),
                rs.getBigDecimal("amount"),
                rs.getString("currency").trim(),
                rs.getString("idempotency_key"),
                rs.getString("status"),
                rs.getString("provider")
            ), orderId);
        if (payments.isEmpty()) {
            throw new IllegalArgumentException("Payment record not found for this order.");
        }

        PaymentAttempt attempt = payments.get(0);
        if (!expectedProvider.equalsIgnoreCase(attempt.provider())) {
            throw new IllegalArgumentException("No configured payment provider can process this payment.");
        }
        if (!"PENDING".equals(attempt.status())) {
            return new Claim(attempt, false);
        }

        int updated = jdbc.update("""
            UPDATE payments
            SET status = 'PROCESSING', updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'PENDING'
            """, attempt.paymentId());
        return new Claim(
            new PaymentAttempt(attempt.paymentId(), attempt.orderId(), attempt.amount(),
                attempt.currency(), attempt.idempotencyKey(),
                updated == 1 ? "PROCESSING" : attempt.status(), attempt.provider()),
            updated == 1
        );
    }

    @Transactional
    public void complete(PaymentAttempt attempt, PaymentProvider.ProviderResult result) {
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT o.order_status, o.discount_code_id
            FROM orders o
            JOIN payments p ON p.order_id = o.id
            WHERE o.id = ? AND p.id = ?
            FOR UPDATE OF o, p
            """, attempt.orderId(), attempt.paymentId());
        if (orders.isEmpty()) {
            throw new IllegalStateException("Payment order no longer exists.");
        }

        List<String> statuses = jdbc.query(
            "SELECT status FROM payments WHERE id = ? FOR UPDATE",
            (rs, row) -> rs.getString("status"), attempt.paymentId());
        if (statuses.isEmpty() || !"PROCESSING".equals(statuses.get(0))) {
            return;
        }

        String orderStatus = String.valueOf(orders.get(0).get("order_status"));
        Long discountCodeId = nullableLong(orders.get(0).get("discount_code_id"));
        switch (result.outcome()) {
            case SUCCEEDED -> {
                jdbc.update("""
                    UPDATE payments SET status = 'SUCCEEDED', provider_reference = ?,
                        updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, result.reference(), attempt.paymentId());
                jdbc.update("""
                    UPDATE orders SET payment_status = 'PAID', updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, attempt.orderId());
            }
            case PENDING -> jdbc.update("""
                UPDATE payments SET status = 'PENDING', provider_reference = ?,
                    updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, result.reference(), attempt.paymentId());
            case FAILED -> {
                jdbc.update("""
                    UPDATE payments SET status = 'FAILED', provider_reference = ?,
                        updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, result.reference(), attempt.paymentId());
                jdbc.update("""
                    UPDATE orders SET payment_status = 'FAILED', order_status = 'CANCELLED',
                        updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, attempt.orderId());
                if (!"CANCELLED".equalsIgnoreCase(orderStatus)) {
                    inventory.restoreOrder(attempt.orderId(), discountCodeId);
                }
            }
        }
    }

    @Transactional
    public void releaseClaim(PaymentAttempt attempt) {
        jdbc.update("""
            UPDATE payments SET status = 'PENDING', updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'PROCESSING'
            """, attempt.paymentId());
    }

    public PaymentStatus status(Long userId, Long orderId) {
        List<PaymentStatus> statuses = jdbc.query("""
            SELECT p.status, p.provider_reference, p.amount, p.currency, p.provider
            FROM payments p
            JOIN orders o ON o.id = p.order_id
            WHERE o.user_id = ? AND o.id = ?
            """, (rs, row) -> new PaymentStatus(
                rs.getString("status"),
                rs.getString("provider_reference"),
                rs.getBigDecimal("amount"),
                rs.getString("currency").trim(),
                rs.getString("provider")
            ), userId, orderId);
        if (statuses.isEmpty()) {
            throw new IllegalArgumentException("Payment record not found for this order.");
        }
        return statuses.get(0);
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    public record PaymentAttempt(
        Long paymentId,
        Long orderId,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        String status,
        String provider
    ) {}

    public record Claim(PaymentAttempt attempt, boolean claimed) {}

    public record PaymentStatus(
        String status,
        String providerReference,
        BigDecimal amount,
        String currency,
        String provider
    ) {}
}
