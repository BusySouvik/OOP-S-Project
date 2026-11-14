package com.vcart.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class OwnerAnalyticsService {
    private final JdbcTemplate jdbc;
    public OwnerAnalyticsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private void requireAdmin(Long userId) {
        List<String> roles = jdbc.query("SELECT role FROM users WHERE id=? AND is_active=TRUE",
            (rs, n) -> rs.getString(1), userId);
        if (roles.isEmpty() || !"ADMIN".equalsIgnoreCase(roles.get(0)))
            throw new org.springframework.security.access.AccessDeniedException("Owner access required.");
    }

    public Map<String,Object> overview(Long userId) {
        requireAdmin(userId);
        Map<String,Object> totals = jdbc.queryForMap("""
            SELECT COUNT(*) FILTER (WHERE o.order_status <> 'CANCELLED') AS orders,
                   COALESCE(SUM(o.total_amount) FILTER (
                       WHERE o.payment_status = 'PAID'
                         AND p.status = 'SUCCEEDED'
                         AND p.provider IN ('RAZORPAY', 'STRIPE')),0) AS revenue,
                   COUNT(*) FILTER (WHERE o.created_at >= CURRENT_DATE
                       AND o.order_status <> 'CANCELLED') AS today_orders,
                   COALESCE(SUM(o.total_amount) FILTER (
                       WHERE o.created_at >= CURRENT_DATE
                         AND o.payment_status = 'PAID'
                         AND p.status = 'SUCCEEDED'
                         AND p.provider IN ('RAZORPAY', 'STRIPE')),0) AS today_revenue,
                   COUNT(DISTINCT o.user_id) FILTER (
                       WHERE o.payment_status = 'PAID'
                         AND p.status = 'SUCCEEDED'
                         AND p.provider IN ('RAZORPAY', 'STRIPE')) AS buying_customers
            FROM orders o
            LEFT JOIN payments p ON p.order_id = o.id
        """);
        Map<String,Object> stock = jdbc.queryForMap("""
            SELECT COUNT(*) FILTER (WHERE is_active=TRUE) AS active_products,
                   COUNT(*) FILTER (WHERE is_active=TRUE AND stock_quantity<=5) AS low_stock,
                   COALESCE(SUM(stock_quantity),0) AS units_in_stock
            FROM products
        """);
        Map<String,Object> customer = jdbc.queryForMap("SELECT COUNT(*) AS customers FROM users WHERE role='CUSTOMER' AND is_active=TRUE");
        Map<String,Object> result = new LinkedHashMap<>();
        result.putAll(totals); result.putAll(stock); result.putAll(customer);
        return result;
    }

    public List<Map<String,Object>> dailySales(Long userId) {
        requireAdmin(userId);
        return jdbc.queryForList("""
            SELECT TO_CHAR(day_value::date,'Mon DD') AS day,
                   COALESCE(SUM(o.total_amount),0) AS revenue,
                   COUNT(o.id) AS orders
            FROM generate_series(CURRENT_DATE - INTERVAL '6 days', CURRENT_DATE, INTERVAL '1 day') day_value
            LEFT JOIN orders o ON o.created_at::date=day_value::date
                AND o.payment_status='PAID'
                AND o.order_status<>'CANCELLED'
            LEFT JOIN payments p ON p.order_id=o.id
                AND p.status='SUCCEEDED'
                AND p.provider IN ('RAZORPAY','STRIPE')
            WHERE o.id IS NULL OR p.id IS NOT NULL
            GROUP BY day_value ORDER BY day_value
        """);
    }

    public List<Map<String,Object>> topProducts(Long userId) {
        requireAdmin(userId);
        return jdbc.queryForList("""
            SELECT oi.product_name AS name, SUM(oi.quantity) AS units_sold,
                   COALESCE(SUM(oi.line_total),0) AS sales
            FROM order_items oi JOIN orders o ON o.id=oi.order_id
            JOIN payments p ON p.order_id=o.id
            WHERE o.order_status<>'CANCELLED'
              AND o.payment_status='PAID'
              AND p.status='SUCCEEDED'
              AND p.provider IN ('RAZORPAY','STRIPE')
            GROUP BY oi.product_name ORDER BY units_sold DESC, sales DESC LIMIT 8
        """);
    }

    public List<Map<String,Object>> recentOrders(Long userId) {
        requireAdmin(userId);
        return jdbc.queryForList("""
            SELECT o.order_number, u.full_name AS customer, o.total_amount,
                   o.order_status AS status, o.payment_status, p.provider,
                   p.status AS payment_attempt_status, o.created_at
            FROM orders o JOIN users u ON u.id=o.user_id
            LEFT JOIN payments p ON p.order_id=o.id
            ORDER BY o.created_at DESC LIMIT 10
        """);
    }
}
