package com.vcart.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.List;
import java.util.Objects;

@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    private final OrderInventoryService inventory;

    public AdminService(JdbcTemplate jdbc, OrderInventoryService inventory) {
        this.jdbc = jdbc;
        this.inventory = inventory;
    }

    private void requireAdmin(Long userId) {
        List<String> roles = jdbc.query(
            "SELECT role FROM users WHERE id = ? AND is_active = TRUE",
            (rs, row) -> rs.getString("role"), userId
        );

        if (roles.isEmpty() || !"ADMIN".equalsIgnoreCase(roles.get(0)))
            throw new IllegalArgumentException("Admin access required.");
    }

    public Map<String, Object> dashboard(Long userId) {
        requireAdmin(userId);

        Integer users = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users", Integer.class);
        Integer products = jdbc.queryForObject(
            "SELECT COUNT(*) FROM products WHERE is_active = TRUE", Integer.class);
        Integer orders = jdbc.queryForObject(
            "SELECT COUNT(*) FROM orders", Integer.class);
        Integer pending = jdbc.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE order_status = 'PLACED'", Integer.class);
        Object revenue = jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(o.total_amount), 0)
            FROM orders o JOIN payments p ON p.order_id = o.id
            WHERE o.payment_status = 'PAID'
              AND p.status = 'SUCCEEDED'
              AND p.provider IN ('RAZORPAY', 'STRIPE')
            """,
            Object.class);

        return Map.of(
            "totalUsers", users,
            "activeProducts", products,
            "totalOrders", orders,
            "pendingOrders", pending,
            "revenue", revenue
        );
    }

    public List<Map<String, Object>> allOrders(Long userId) {
        requireAdmin(userId);
        return jdbc.queryForList("""
            SELECT o.id, o.order_number, o.user_id, u.full_name,
                   o.total_amount, o.order_status AS status,
                   o.payment_status, p.provider, p.status AS payment_attempt_status,
                   o.created_at
            FROM orders o
            JOIN users u ON u.id = o.user_id
            LEFT JOIN payments p ON p.order_id = o.id
            ORDER BY o.created_at DESC
            """);
    }

    @Transactional
    public void updateOrderStatus(Long userId, Long orderId, String status) {
        requireAdmin(userId);

        String nextStatus = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("PLACED", "PROCESSING", "SHIPPED", "DELIVERED",
                     "CANCELLED").contains(nextStatus))
            throw new IllegalArgumentException("Invalid order status.");

        List<Map<String,Object>> matches = jdbc.queryForList("""
            SELECT o.id, o.order_status AS status, o.discount_code_id, o.payment_status,
                   EXISTS (
                       SELECT 1 FROM payments p
                       WHERE p.order_id = o.id
                         AND p.status = 'SUCCEEDED'
                         AND p.provider IN ('RAZORPAY', 'STRIPE')
                   ) AS verified_payment
            FROM orders o WHERE o.id = ? FOR UPDATE
            """, orderId);
        if (matches.isEmpty()) throw new IllegalArgumentException("Order not found.");

        Map<String,Object> order = matches.get(0);
        String currentStatus = Objects.toString(order.get("status"), "").toUpperCase();
        String paymentStatus = Objects.toString(order.get("payment_status"), "").toUpperCase();

        validateStatusTransition(currentStatus, nextStatus);

        if (List.of("PROCESSING", "SHIPPED", "DELIVERED").contains(nextStatus)
                && (!"PAID".equals(paymentStatus) ||
                    !Boolean.TRUE.equals(order.get("verified_payment")))) {
            throw new IllegalArgumentException(
                "Orders can only be fulfilled after payment is verified by a real provider.");
        }

        if ("CANCELLED".equals(nextStatus) && !"CANCELLED".equals(currentStatus)) {
            if ("PAID".equals(paymentStatus) || "REFUNDED".equals(paymentStatus)) {
                throw new IllegalArgumentException(
                    "Paid orders require refund processing before cancellation."
                );
            }
            List<String> attemptStatuses = jdbc.query(
                "SELECT status FROM payments WHERE order_id = ? FOR UPDATE",
                (rs, row) -> rs.getString("status"), orderId);
            if (attemptStatuses.stream().anyMatch(attemptStatus -> "PROCESSING".equals(attemptStatus))) {
                throw new IllegalArgumentException("Wait for the payment attempt to finish before cancelling.");
            }
            Object value = order.get("discount_code_id");
            Long discountCodeId = value == null ? null : ((Number) value).longValue();
            inventory.restoreOrder(orderId, discountCodeId);
        }

        jdbc.update("""
            UPDATE orders SET order_status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """, nextStatus, orderId);
    }

    static void validateStatusTransition(String currentStatus, String nextStatus) {
        if (currentStatus.equals(nextStatus)) {
            return;
        }

        boolean allowed = switch (currentStatus) {
            case "PLACED", "CONFIRMED" ->
                "PROCESSING".equals(nextStatus) || "CANCELLED".equals(nextStatus);
            case "PROCESSING" ->
                "SHIPPED".equals(nextStatus) || "CANCELLED".equals(nextStatus);
            case "SHIPPED" -> "DELIVERED".equals(nextStatus);
            case "DELIVERED", "CANCELLED" -> false;
            default -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException(
                "Invalid order status transition from " + currentStatus + " to " + nextStatus + "."
            );
        }
    }
}