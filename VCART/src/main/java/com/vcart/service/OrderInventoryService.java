package com.vcart.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OrderInventoryService {
    private final JdbcTemplate jdbc;

    public OrderInventoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void restoreOrder(Long orderId, Long discountCodeId) {
        List<Map<String, Object>> items = jdbc.queryForList("""
            SELECT product_id, quantity
            FROM order_items
            WHERE order_id = ? AND product_id IS NOT NULL
            ORDER BY product_id
            """, orderId);

        for (Map<String, Object> item : items) {
            int restored = jdbc.update("""
                UPDATE products
                SET stock_quantity = stock_quantity + ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, item.get("quantity"), item.get("product_id"));
            if (restored != 1) {
                throw new IllegalStateException("Could not restore inventory for a cancelled order.");
            }
        }

        if (discountCodeId != null) {
            jdbc.update("""
                UPDATE discount_codes
                SET used_count = GREATEST(used_count - 1, 0)
                WHERE id = ?
                """, discountCodeId);
        }
    }
}
