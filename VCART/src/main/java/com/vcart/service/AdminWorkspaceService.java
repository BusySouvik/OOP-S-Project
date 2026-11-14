package com.vcart.service;

import com.vcart.service.payment.RefundService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AdminWorkspaceService {
    private final JdbcTemplate jdbc;
    private final StaffAccountService staffAccounts;
    private final RefundService refundService;

    public AdminWorkspaceService(
        JdbcTemplate jdbc,
        StaffAccountService staffAccounts,
        RefundService refundService
    ) {
        this.jdbc = jdbc;
        this.staffAccounts = staffAccounts;
        this.refundService = refundService;
    }

    private void admin(Long userId) {
        List<String> roles = jdbc.query(
            "SELECT role FROM users WHERE id=? AND is_active=TRUE",
            (rs, n) -> rs.getString(1), userId);
        if (roles.isEmpty() || !"ADMIN".equalsIgnoreCase(roles.get(0)))
            throw new IllegalArgumentException("Administrator access required.");
    }

    public List<Map<String,Object>> categories(Long uid) {
        admin(uid);
        return jdbc.queryForList(
            "SELECT id,name,description,is_active,created_at FROM categories ORDER BY name");
    }

    @Transactional
    public Map<String,Object> createCategory(Long uid, Map<String,Object> data) {
        admin(uid);
        String name = Objects.toString(data.get("name"), "").trim();
        if (name.isBlank() || name.length() > 100) throw new IllegalArgumentException("Category name is required (up to 100 characters).");
        jdbc.update("INSERT INTO categories(name,description) VALUES (?,?)",
            name, data.get("description"));
        return Map.of("message","Category created.");
    }

    @Transactional
    public Map<String,Object> updateCategory(Long uid, Long id, Map<String,Object> data) {
        admin(uid);
        String name = Objects.toString(data.get("name"), "").trim();
        if (name.isBlank() || name.length() > 100) throw new IllegalArgumentException("Category name is required (up to 100 characters).");
        int count = jdbc.update(
            "UPDATE categories SET name=?,description=? WHERE id=?",
            name, data.get("description"), id);
        if (count == 0) throw new IllegalArgumentException("Category not found.");
        return Map.of("message","Category updated.");
    }

    public Map<String,Object> deactivateCategory(Long uid, Long id) {
        admin(uid);
        int count = jdbc.update("UPDATE categories SET is_active=FALSE WHERE id=?", id);
        if (count == 0) throw new IllegalArgumentException("Category not found.");
        return Map.of("message","Category deactivated.");
    }

    public List<Map<String,Object>> customers(Long uid) {
        admin(uid);
        return jdbc.queryForList("""
            SELECT id,full_name,email,phone,role,is_active,created_at
            FROM users WHERE role='CUSTOMER' ORDER BY created_at DESC
        """);
    }

    public Map<String,Object> customerActive(Long uid, Long customerId, boolean active) {
        admin(uid);
        int count = jdbc.update(
            "UPDATE users SET is_active=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND role='CUSTOMER'",
            active, customerId);
        if (count == 0) throw new IllegalArgumentException("Customer not found.");
        return Map.of("message","Customer account updated.");
    }

    public List<Map<String,Object>> inventoryAlerts(Long uid) {
        admin(uid);
        return jdbc.queryForList("""
            SELECT p.id,p.name,p.sku,p.stock_quantity,p.price,c.name AS category
            FROM products p LEFT JOIN categories c ON c.id=p.category_id
            WHERE p.is_active=TRUE AND p.stock_quantity<=5
            ORDER BY p.stock_quantity ASC
        """);
    }

    public Map<String,Object> updateStock(Long uid, Long productId, Integer quantity) {
        admin(uid);
        if (quantity == null || quantity < 0)
            throw new IllegalArgumentException("Stock cannot be negative.");
        int count = jdbc.update("""
            UPDATE products SET stock_quantity=?,updated_at=CURRENT_TIMESTAMP
            WHERE id=? AND is_active=TRUE
        """, quantity, productId);
        if (count == 0) throw new IllegalArgumentException("Product not found.");
        return Map.of("message","Inventory updated.","quantity",quantity);
    }

    public List<Map<String,Object>> transactions(Long uid) {
        admin(uid);
        return jdbc.queryForList("""
            SELECT o.id,o.order_number,o.user_id,o.total_amount,o.payment_status,
                   o.order_status AS status,o.created_at,p.status AS payment_attempt_status,
                   p.provider,p.provider_reference,p.refund_reference
            FROM orders o
            LEFT JOIN payments p ON p.order_id=o.id
            ORDER BY o.created_at DESC
        """);
    }

    public List<Map<String,Object>> refunds(Long uid) {
        return refundService.ownerList(uid);
    }

    public Map<String,Object> createRefund(Long uid, Map<String,Object> data) {
        if (data == null || data.get("orderId") == null) {
            throw new IllegalArgumentException("Order ID is required.");
        }
        Long orderId;
        try {
            orderId = Long.valueOf(data.get("orderId").toString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("A valid order ID is required.", ex);
        }
        return refundService.requestForOwner(
            uid, orderId, Objects.toString(data.get("reason"), ""));
    }

    public Map<String,Object> updateRefund(Long uid, Long refundId, Map<String,Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("Refund review details are required.");
        }
        return refundService.review(
            uid, refundId, Objects.toString(data.get("status"), ""),
            Objects.toString(data.get("adminNote"), ""));
    }

    public List<Map<String,Object>> staff(Long uid) {
        return staffAccounts.list(uid);
    }

    public Map<String,Object> createStaff(Long uid, Map<String,Object> data) {
        return staffAccounts.create(uid, data);
    }

    public Map<String,Object> staffActive(Long uid, Long staffId, boolean active) {
        return staffAccounts.setActive(uid, staffId, active);
    }

    public Map<String,Object> updateStaff(Long uid, Long staffId, Map<String,Object> data) {
        return staffAccounts.update(uid, staffId, data);
    }

    public Map<String,Object> enableStaffAccount(Long uid, Long staffId, Map<String,Object> data) {
        return staffAccounts.enableAccount(uid, staffId, data);
    }

    public Map<String,Object> resetStaffPassword(Long uid, Long staffId, Map<String,Object> data) {
        return staffAccounts.resetPassword(uid, staffId, data);
    }

    public Map<String,Object> processRefund(Long uid, Long refundId) {
        return refundService.process(uid, refundId);
    }

    public List<Map<String,Object>> support(Long uid) {
        admin(uid);
        return jdbc.queryForList("""
            SELECT s.id,s.user_id,s.subject,s.message,s.status,s.created_at,u.email
            FROM support_messages s LEFT JOIN users u ON u.id=s.user_id
            ORDER BY s.created_at DESC
        """);
    }

    public Map<String,Object> createSupport(Long uid, Map<String,Object> data) {
        admin(uid);
        String subject = Objects.toString(data.get("subject"), "").trim();
        String message = Objects.toString(data.get("message"), "").trim();
        if (subject.isBlank() || message.isBlank())
            throw new IllegalArgumentException("Subject and message are required.");
        jdbc.update("INSERT INTO support_messages(user_id,subject,message) VALUES (?,?,?)",
            uid, subject, message);
        return Map.of("message","Support message created.");
    }

    public Map<String,Object> updateSupport(Long uid, Long id, Map<String,Object> data) {
        admin(uid);
        String status = Objects.toString(data.get("status"), "").toUpperCase();
        if (!List.of("OPEN","IN_PROGRESS","RESOLVED").contains(status))
            throw new IllegalArgumentException("Invalid support status.");
        int count = jdbc.update("""
            UPDATE support_messages SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?
        """, status, id);
        if (count == 0) throw new IllegalArgumentException("Support message not found.");
        return Map.of("message","Support status updated.");
    }

    public Map<String,Object> finance(Long uid) {
        admin(uid);
        Map<String,Object> totals = jdbc.queryForMap("""
            SELECT COUNT(*) AS order_count,
                   COALESCE(SUM(o.total_amount),0) AS gross_order_value,
                   COALESCE(SUM(CASE WHEN o.payment_status='PAID'
                       AND p.status='SUCCEEDED'
                       AND p.provider IN ('RAZORPAY','STRIPE')
                       THEN o.total_amount ELSE 0 END),0) AS verified_revenue,
                   COALESCE(SUM(CASE WHEN p.provider='MOCK' AND p.status='SUCCEEDED'
                       THEN o.total_amount ELSE 0 END),0) AS simulated_paid_amount,
                   COUNT(*) FILTER (WHERE o.payment_status='PENDING') AS pending_payments,
                   COUNT(*) FILTER (WHERE o.payment_status='FAILED') AS failed_payments
            FROM orders o LEFT JOIN payments p ON p.order_id=o.id
            WHERE o.order_status<>'CANCELLED'
        """);
        return totals;
    }
}