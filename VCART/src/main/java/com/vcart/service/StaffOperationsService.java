package com.vcart.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StaffOperationsService {
    private final JdbcTemplate jdbc;

    public StaffOperationsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> orders(String email) {
        requireActiveStaff(email);
        return jdbc.queryForList("""
            SELECT o.id, o.order_number, o.order_status AS status, o.payment_status,
                   o.shipping_address, o.total_amount, o.created_at, u.full_name
            FROM orders o
            JOIN users u ON u.id = o.user_id
            WHERE o.order_status <> 'CANCELLED'
              AND o.payment_status = 'PAID'
              AND EXISTS (
                  SELECT 1 FROM payments p
                  WHERE p.order_id = o.id AND p.status = 'SUCCEEDED'
                    AND p.provider IN ('RAZORPAY', 'STRIPE')
              )
            ORDER BY o.created_at DESC
            """);
    }

    @Transactional
    public Map<String, Object> updateOrderStatus(String email, Long orderId, String status) {
        requireActiveStaff(email);
        String nextStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PROCESSING", "SHIPPED", "DELIVERED").contains(nextStatus)) {
            throw new IllegalArgumentException("Staff may only advance fulfillment status.");
        }

        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT o.order_status, o.payment_status,
                   EXISTS (
                       SELECT 1 FROM payments p
                       WHERE p.order_id = o.id
                         AND p.status = 'SUCCEEDED'
                         AND p.provider IN ('RAZORPAY', 'STRIPE')
                   ) AS verified_payment
            FROM orders o WHERE o.id = ? FOR UPDATE
            """, orderId);
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Order not found.");
        }
        Map<String, Object> order = orders.get(0);
        String currentStatus = String.valueOf(order.get("order_status")).toUpperCase(Locale.ROOT);
        if (!"PAID".equalsIgnoreCase(String.valueOf(order.get("payment_status"))) ||
                !Boolean.TRUE.equals(order.get("verified_payment"))) {
            throw new IllegalArgumentException(
                "Orders can only be fulfilled after payment is verified by a real provider.");
        }
        AdminService.validateStatusTransition(currentStatus, nextStatus);
        jdbc.update("""
            UPDATE orders SET order_status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, nextStatus, orderId);
        return Map.of("message", "Order fulfillment status updated.", "status", nextStatus);
    }

    public List<Map<String, Object>> support(String email) {
        requireActiveStaff(email);
        return jdbc.queryForList("""
            SELECT s.id, s.subject, s.message, s.status, s.created_at,
                   u.full_name, u.email
            FROM support_messages s
            LEFT JOIN users u ON u.id = s.user_id
            ORDER BY s.created_at DESC
            """);
    }

    public Map<String, Object> updateSupportStatus(String email, Long supportId, String status) {
        requireActiveStaff(email);
        String nextStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("OPEN", "IN_PROGRESS", "RESOLVED").contains(nextStatus)) {
            throw new IllegalArgumentException("Invalid support status.");
        }
        int updated = jdbc.update("""
            UPDATE support_messages SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, nextStatus, supportId);
        if (updated != 1) {
            throw new IllegalArgumentException("Support request not found.");
        }
        return Map.of("message", "Support status updated.", "status", nextStatus);
    }

    private void requireActiveStaff(String email) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM users u
            JOIN staff_members s ON s.user_id = u.id
            WHERE LOWER(u.email) = LOWER(?)
              AND u.role = 'STAFF' AND u.is_active = TRUE AND s.is_active = TRUE
            """, Integer.class, email);
        if (count == null || count != 1) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Active staff account required.");
        }
    }
}
