package com.vcart.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
public class CustomerFeatureService {
    private final JdbcTemplate jdbc;
    public CustomerFeatureService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private Long customerId(String email) {
        List<Long> ids = jdbc.query("SELECT id FROM users WHERE LOWER(email)=LOWER(?) AND role='CUSTOMER' AND is_active=TRUE", (rs, n) -> rs.getLong(1), email);
        if (ids.isEmpty()) throw new IllegalArgumentException("Active customer account not found.");
        return ids.get(0);
    }

    public Map<String,Object> profile(String email) {
        Long id = customerId(email);
        return jdbc.queryForMap("SELECT id, full_name, email, phone FROM users WHERE id=?", id);
    }

    public Map<String,Object> updateProfile(String email, Map<String,String> body) {
        if (body == null) throw new IllegalArgumentException("Profile details are required.");
        Long id = customerId(email);
        String name = body.get("fullName");
        String phone = body.get("phone");
        if (phone == null) phone = "";
        if (name == null || name.isBlank() || name.length() > 100) throw new IllegalArgumentException("Enter a name up to 100 characters.");
        if (phone.length() > 20) throw new IllegalArgumentException("Phone number cannot exceed 20 characters.");
        jdbc.update("UPDATE users SET full_name=?, phone=? WHERE id=?", name.trim(), phone.trim(), id);
        return profile(email);
    }

    public List<Map<String,Object>> wishlist(String email) {
        Long id = customerId(email);
        return jdbc.queryForList("""
            SELECT p.id, p.name, p.description, p.price, p.image_url, p.stock_quantity
            FROM wishlist_items w JOIN products p ON p.id=w.product_id
            WHERE w.user_id=? AND p.is_active=TRUE ORDER BY w.created_at DESC
            """, id);
    }

    @Transactional
    public Map<String,Object> addWishlist(String email, Long productId) {
        Long id = customerId(email);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=? AND is_active=TRUE", Integer.class, productId);
        if (exists == null || exists == 0) throw new IllegalArgumentException("Product is not available.");
        jdbc.update("INSERT INTO wishlist_items(user_id, product_id) VALUES (?, ?) ON CONFLICT(user_id, product_id) DO NOTHING", id, productId);
        return Map.of("message", "Product saved to your wishlist.");
    }

    public Map<String,Object> removeWishlist(String email, Long productId) {
        Long id = customerId(email);
        jdbc.update("DELETE FROM wishlist_items WHERE user_id=? AND product_id=?", id, productId);
        return Map.of("message", "Product removed from your wishlist.");
    }

    public List<Map<String,Object>> supportMessages(String email) {
        Long id = customerId(email);
        return jdbc.queryForList("SELECT id, subject, message, status, created_at, updated_at FROM support_messages WHERE user_id=? ORDER BY created_at DESC", id);
    }

    public Map<String,Object> createSupportMessage(String email, Map<String,String> body) {
        if (body == null) throw new IllegalArgumentException("Support request details are required.");
        Long id = customerId(email);
        String subject = body.get("subject"), message = body.get("message");
        if (subject == null || subject.isBlank() || subject.length() > 160 || message == null || message.isBlank() || message.length() > 4000)
            throw new IllegalArgumentException("Enter a subject and message (maximum 4,000 characters).");
        Long ticketId = jdbc.queryForObject("INSERT INTO support_messages(user_id, subject, message, status) VALUES (?, ?, ?, 'OPEN') RETURNING id", Long.class, id, subject.trim(), message.trim());
        return Map.of("message", "Support request submitted.", "ticketId", ticketId);
    }
}
