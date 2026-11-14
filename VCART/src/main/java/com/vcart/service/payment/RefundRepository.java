package com.vcart.service.payment;

import com.vcart.service.OrderInventoryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Repository
public class RefundRepository {
    private final JdbcTemplate jdbc;
    private final OrderInventoryService inventory;

    public RefundRepository(JdbcTemplate jdbc, OrderInventoryService inventory) {
        this.jdbc = jdbc;
        this.inventory = inventory;
    }

    @Transactional
    public Map<String, Object> requestForCustomer(Long userId, Long orderId, String reason) {
        validateReason(reason);
        return createRequest(userId, orderId, reason.trim());
    }

    @Transactional
    public Map<String, Object> requestForOwner(Long ownerId, Long orderId, String reason) {
        requireRole(ownerId, "ADMIN");
        validateReason(reason);
        List<Long> customers = jdbc.query("""
            SELECT user_id FROM orders WHERE id = ?
            """, (rs, row) -> rs.getLong("user_id"), orderId);
        if (customers.isEmpty()) {
            throw new IllegalArgumentException("Order not found.");
        }
        return createRequest(customers.get(0), orderId, reason.trim());
    }

    public Map<String, Object> customerStatus(Long userId, Long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT r.id, r.order_id, r.reason, r.status, r.admin_note,
                   r.provider_reference, p.provider AS payment_provider,
                   p.status AS payment_status, r.created_at, r.updated_at
            FROM refund_requests r
            JOIN orders o ON o.id = r.order_id
            JOIN payments p ON p.id = r.payment_id
            WHERE o.user_id = ? AND o.id = ?
            """, userId, orderId);
        if (rows.isEmpty()) {
            return Map.of("status", "NONE");
        }
        return rows.get(0);
    }

    public List<Map<String, Object>> ownerList(Long ownerId) {
        requireRole(ownerId, "ADMIN");
        return jdbc.queryForList("""
            SELECT r.id, r.order_id, r.user_id, r.reason, r.status, r.admin_note,
                   r.provider_reference, r.created_at, r.updated_at,
                   o.order_number, o.total_amount, p.currency,
                   p.provider AS payment_provider, p.status AS payment_status
            FROM refund_requests r
            JOIN orders o ON o.id = r.order_id
            JOIN payments p ON p.id = r.payment_id
            ORDER BY r.created_at DESC
            """);
    }

    @Transactional
    public Map<String, Object> review(Long ownerId, Long refundId, String status, String note) {
        requireRole(ownerId, "ADMIN");
        String nextStatus = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("APPROVED", "REJECTED").contains(nextStatus)) {
            throw new IllegalArgumentException("Refund review must approve or reject a request.");
        }
        String adminNote = Objects.toString(note, "").trim();
        if (adminNote.length() > 2000) {
            throw new IllegalArgumentException("Refund note cannot exceed 2,000 characters.");
        }
        List<String> statuses = jdbc.query("""
            SELECT status FROM refund_requests WHERE id = ? FOR UPDATE
            """, (rs, row) -> rs.getString("status"), refundId);
        if (statuses.isEmpty()) {
            throw new IllegalArgumentException("Refund request not found.");
        }
        if (!"REQUESTED".equals(statuses.get(0))) {
            throw new IllegalArgumentException("Only a requested refund can be reviewed.");
        }
        jdbc.update("""
            UPDATE refund_requests
            SET status = ?, admin_note = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'REQUESTED'
            """, nextStatus, adminNote, refundId);
        return Map.of("message", "Refund request " + nextStatus.toLowerCase() + ".");
    }

    @Transactional
    public RefundAttempt claimProcessing(Long ownerId, Long refundId) {
        requireRole(ownerId, "ADMIN");
        List<RefundAttempt> rows = jdbc.query("""
            SELECT r.id AS refund_id, r.order_id, r.payment_id, r.status AS refund_status,
                   r.idempotency_key, o.order_status, o.discount_code_id,
                   o.payment_status, p.status AS payment_status_record,
                   p.amount, p.currency, p.provider
            FROM refund_requests r
            JOIN orders o ON o.id = r.order_id
            JOIN payments p ON p.id = r.payment_id
            WHERE r.id = ?
            FOR UPDATE OF r, o, p
            """, (rs, row) -> new RefundAttempt(
                rs.getLong("refund_id"),
                rs.getLong("order_id"),
                rs.getLong("payment_id"),
                rs.getString("idempotency_key"),
                rs.getBigDecimal("amount"),
                rs.getString("currency").trim(),
                rs.getString("order_status"),
                nullableLong(rs.getObject("discount_code_id")),
                rs.getString("provider")
            ), refundId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Refund request not found.");
        }

        RefundAttempt attempt = rows.get(0);
        String refundStatus = jdbc.queryForObject(
            "SELECT status FROM refund_requests WHERE id = ?", String.class, refundId);
        String paymentStatus = jdbc.queryForObject(
            "SELECT status FROM payments WHERE id = ?", String.class, attempt.paymentId());
        String orderPaymentStatus = jdbc.queryForObject(
            "SELECT payment_status FROM orders WHERE id = ?", String.class, attempt.orderId());
        if (!"APPROVED".equals(refundStatus)) {
            return null;
        }
        if (!"SUCCEEDED".equals(paymentStatus) || !"PAID".equals(orderPaymentStatus)) {
            throw new IllegalArgumentException("Only successfully paid orders can be refunded.");
        }
        if (!"MOCK".equalsIgnoreCase(attempt.provider())) {
            throw new IllegalArgumentException("No configured refund provider can process this payment.");
        }
        jdbc.update("""
            UPDATE refund_requests SET status = 'PROCESSING', updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'APPROVED'
            """, refundId);
        return attempt;
    }

    @Transactional
    public void complete(RefundAttempt attempt, PaymentProvider.ProviderResult result) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT r.status AS refund_status, o.order_status, o.discount_code_id,
                   p.status AS payment_status
            FROM refund_requests r
            JOIN orders o ON o.id = r.order_id
            JOIN payments p ON p.id = r.payment_id
            WHERE r.id = ?
            FOR UPDATE OF r, o, p
            """, attempt.refundId());
        if (rows.isEmpty()) {
            throw new IllegalStateException("Refund record no longer exists.");
        }
        Map<String, Object> row = rows.get(0);
        if (!"PROCESSING".equals(row.get("refund_status"))) {
            return;
        }

        switch (result.outcome()) {
            case PENDING -> jdbc.update("""
                UPDATE refund_requests
                SET provider_reference = ?, admin_note = 'Provider reports pending; awaiting completion.',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, result.reference(), attempt.refundId());
            case FAILED -> jdbc.update("""
                UPDATE refund_requests
                SET status = 'APPROVED', provider_reference = ?,
                    admin_note = 'Provider reported failure; a retry is available.',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, result.reference(), attempt.refundId());
            case SUCCEEDED -> completeSuccessfulRefund(attempt, row, result.reference());
        }
    }

    @Transactional
    public void releaseProcessing(RefundAttempt attempt) {
        jdbc.update("""
            UPDATE refund_requests
            SET status = 'APPROVED',
                admin_note = 'Provider call did not complete; retry is available.',
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'PROCESSING'
            """, attempt.refundId());
    }

    public Map<String, Object> ownerStatus(Long ownerId, Long refundId) {
        requireRole(ownerId, "ADMIN");
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT r.id, r.order_id, r.status, r.admin_note, r.provider_reference,
                   p.provider AS payment_provider,
                   p.status AS payment_status, o.payment_status AS order_payment_status,
                   o.order_status AS order_status
            FROM refund_requests r
            JOIN payments p ON p.id = r.payment_id
            JOIN orders o ON o.id = r.order_id
            WHERE r.id = ?
            """, refundId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Refund request not found.");
        }
        return rows.get(0);
    }

    private Map<String, Object> createRequest(Long userId, Long orderId, String reason) {
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT id, order_status, payment_status
            FROM orders WHERE id = ? AND user_id = ? FOR UPDATE
            """, orderId, userId);
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Order not found.");
        }
        Map<String, Object> order = orders.get(0);
        if (!"PAID".equalsIgnoreCase(String.valueOf(order.get("payment_status"))) ||
                "CANCELLED".equalsIgnoreCase(String.valueOf(order.get("order_status")))) {
            throw new IllegalArgumentException("Only active, paid orders can be refunded.");
        }

        List<Map<String, Object>> payments = jdbc.queryForList("""
            SELECT id, status FROM payments WHERE order_id = ? FOR UPDATE
            """, orderId);
        if (payments.isEmpty() || !"SUCCEEDED".equals(payments.get(0).get("status"))) {
            throw new IllegalArgumentException("A successful payment is required for a refund.");
        }

        Integer existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM refund_requests WHERE order_id = ?",
            Integer.class, orderId);
        if (existing != null && existing > 0) {
            throw new IllegalArgumentException("A refund request already exists for this order.");
        }

        Long refundId = jdbc.queryForObject("""
            INSERT INTO refund_requests
                (payment_id, order_id, user_id, reason, status, idempotency_key)
            VALUES (?, ?, ?, ?, 'REQUESTED', ?)
            RETURNING id
            """, Long.class, payments.get(0).get("id"), orderId, userId, reason,
            UUID.randomUUID().toString());
        return Map.of("refundId", refundId, "status", "REQUESTED");
    }

    private void completeSuccessfulRefund(
        RefundAttempt attempt,
        Map<String, Object> row,
        String providerReference
    ) {
        if (!"SUCCEEDED".equals(row.get("payment_status"))) {
            throw new IllegalStateException("Payment is no longer refundable.");
        }
        jdbc.update("""
            UPDATE refund_requests
            SET status = 'PROCESSED', provider_reference = ?,
                admin_note = 'Development simulation completed; no money was moved.',
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, providerReference, attempt.refundId());
        int updated = jdbc.update("""
            UPDATE payments
            SET status = 'REFUNDED', refund_reference = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'SUCCEEDED'
            """, providerReference, attempt.paymentId());
        if (updated != 1) {
            throw new IllegalStateException("Payment state changed while processing the refund.");
        }

        boolean cancelUnshipped = List.of("PLACED", "CONFIRMED", "PROCESSING")
            .contains(String.valueOf(row.get("order_status")).toUpperCase());
        jdbc.update("""
            UPDATE orders
            SET payment_status = 'REFUNDED',
                order_status = CASE WHEN ? THEN 'CANCELLED' ELSE order_status END,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, cancelUnshipped, attempt.orderId());
        if (cancelUnshipped) {
            inventory.restoreOrder(attempt.orderId(), attempt.discountCodeId());
        }
    }

    private void requireRole(Long userId, String expectedRole) {
        List<String> roles = jdbc.query("""
            SELECT role FROM users WHERE id = ? AND is_active = TRUE
            """, (rs, row) -> rs.getString("role"), userId);
        if (roles.isEmpty() || !expectedRole.equalsIgnoreCase(roles.get(0))) {
            throw new IllegalArgumentException("Authorized account required.");
        }
    }

    private static void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 2000) {
            throw new IllegalArgumentException("Refund reason is required (maximum 2,000 characters).");
        }
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    public record RefundAttempt(
        Long refundId,
        Long orderId,
        Long paymentId,
        String idempotencyKey,
        java.math.BigDecimal amount,
        String currency,
        String orderStatus,
        Long discountCodeId,
        String provider
    ) {}
}
