package com.vcart.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class OwnerReportService {
    private final JdbcTemplate jdbc;
    public OwnerReportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> orders(String email) {
        requireOwner(email);
        return jdbc.queryForList("""
            SELECT o.order_number, u.full_name, u.email, o.subtotal,
                   o.discount_amount, o.total_amount, o.order_status AS status,
                   o.payment_status, p.status AS payment_attempt_status,
                   p.provider AS payment_provider,
                   CASE WHEN p.provider='MOCK' THEN 'SIMULATED - NO MONEY MOVED'
                        WHEN p.provider IN ('RAZORPAY','STRIPE') AND p.status='SUCCEEDED' THEN 'PROVIDER SETTLED'
                        ELSE 'UNVERIFIED' END AS payment_classification,
                   o.shipping_address, o.created_at
            FROM orders o
            JOIN users u ON u.id=o.user_id
            LEFT JOIN payments p ON p.order_id=o.id
            ORDER BY o.created_at DESC
        """);
    }

    public List<Map<String,Object>> products(String email) {
        requireOwner(email);
        return jdbc.queryForList("""
            SELECT p.sku,p.name,c.name AS category,p.price,p.stock_quantity,
                   p.is_active,
                   COALESCE(SUM(CASE WHEN pay.id IS NOT NULL THEN oi.quantity ELSE 0 END),0) AS units_sold
            FROM products p
            LEFT JOIN categories c ON c.id=p.category_id
            LEFT JOIN order_items oi ON oi.product_id=p.id
            LEFT JOIN orders o ON o.id=oi.order_id
                AND o.order_status<>'CANCELLED' AND o.payment_status='PAID'
            LEFT JOIN payments pay ON pay.order_id=o.id
                AND pay.status='SUCCEEDED' AND pay.provider IN ('RAZORPAY','STRIPE')
            GROUP BY p.id,c.name
            ORDER BY units_sold DESC,p.name
        """);
    }

    private void requireOwner(String email) {
        Boolean ok = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM users WHERE LOWER(email)=LOWER(?) AND role='ADMIN' AND is_active=TRUE)",
            Boolean.class, email);
        if (!Boolean.TRUE.equals(ok)) throw new org.springframework.security.access.AccessDeniedException("Owner access required.");
    }
}
