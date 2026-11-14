package com.vcart.service;

import com.vcart.service.payment.PaymentStore;
import com.vcart.service.payment.PaymentProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private final JdbcTemplate jdbc;
    private final PaymentStore payments;
    private final OrderInventoryService inventory;
    private final PaymentProvider paymentProvider;

    public OrderService(
        JdbcTemplate jdbc,
        PaymentStore payments,
        OrderInventoryService inventory,
        PaymentProvider paymentProvider
    ) {
        this.jdbc = jdbc;
        this.payments = payments;
        this.inventory = inventory;
        this.paymentProvider = paymentProvider;
    }

    @Transactional
    public Map<String, Object> checkout(Long userId, Map<String, String> data) {
        if (data == null) {
            throw new IllegalArgumentException("Checkout details are required.");
        }
        String address = data.get("shippingAddress");
        String couponCode = data.get("couponCode");

        if (address == null || address.isBlank() || address.trim().length() > 2000) {
            throw new IllegalArgumentException("Shipping address is required (maximum 2,000 characters).");
        }

        jdbc.update("""
            INSERT INTO carts(user_id)
            SELECT id FROM users WHERE id = ?
            ON CONFLICT(user_id) DO NOTHING
            """, userId);

        List<Long> cartIds = jdbc.query(
            "SELECT id FROM carts WHERE user_id = ? FOR UPDATE",
            (rs, row) -> rs.getLong("id"), userId
        );

        if (cartIds.isEmpty()) {
            throw new IllegalArgumentException("Customer account not found.");
        }

        Long cartId = cartIds.get(0);

        List<Map<String, Object>> items = jdbc.queryForList("""
            SELECT p.id AS product_id, p.name, p.sku, p.price,
                   ci.quantity, p.stock_quantity
            FROM cart_items ci
            JOIN products p ON p.id = ci.product_id
            WHERE ci.cart_id = ? AND p.is_active = TRUE
            FOR UPDATE OF p
            """, cartId);

        if (items.isEmpty()) {
            throw new IllegalArgumentException("Your cart is empty.");
        }

        BigDecimal subtotal = BigDecimal.ZERO;

        for (Map<String, Object> item : items) {
            int quantity = ((Number) item.get("quantity")).intValue();
            int stock = ((Number) item.get("stock_quantity")).intValue();

            if (quantity <= 0 || quantity > stock) {
                throw new IllegalArgumentException(
                    "Insufficient stock for " + item.get("name")
                );
            }

            BigDecimal price = (BigDecimal) item.get("price");
            subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(quantity)));
        }

        BigDecimal discount = BigDecimal.ZERO;
        Long discountCodeId = null;

        if (couponCode != null && !couponCode.isBlank()) {
            List<Map<String, Object>> coupons = jdbc.queryForList("""
                SELECT id, code, discount_type, discount_value,
                       minimum_order_amount, maximum_discount,
                       usage_limit, used_count,
                       (starts_at IS NULL OR starts_at <= CURRENT_TIMESTAMP) AS started,
                       (expires_at IS NULL OR expires_at >= CURRENT_TIMESTAMP) AS not_expired
                FROM discount_codes
                WHERE UPPER(code) = UPPER(?) AND is_active = TRUE
                FOR UPDATE
                """, couponCode.trim());

            if (coupons.isEmpty()) {
                throw new IllegalArgumentException("Invalid or inactive discount code.");
            }

            Map<String, Object> coupon = coupons.get(0);

            if (!Boolean.TRUE.equals(coupon.get("started"))) {
                throw new IllegalArgumentException("Discount code is not active yet.");
            }

            if (!Boolean.TRUE.equals(coupon.get("not_expired"))) {
                throw new IllegalArgumentException("Discount code has expired.");
            }

            BigDecimal minimum = (BigDecimal) coupon.get("minimum_order_amount");

            if (minimum != null && subtotal.compareTo(minimum) < 0) {
                throw new IllegalArgumentException("Minimum order amount not reached.");
            }

            int used = ((Number) coupon.get("used_count")).intValue();
            Object usageLimit = coupon.get("usage_limit");

            if (usageLimit != null && used >= ((Number) usageLimit).intValue()) {
                throw new IllegalArgumentException("Discount code usage limit reached.");
            }

            String type = coupon.get("discount_type").toString();
            BigDecimal value = (BigDecimal) coupon.get("discount_value");

            if ("PERCENTAGE".equalsIgnoreCase(type)) {
                discount = subtotal.multiply(value)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            } else {
                discount = value;
            }

            Object maximum = coupon.get("maximum_discount");

            if (maximum != null) {
                discount = discount.min((BigDecimal) maximum);
            }

            discount = discount.min(subtotal).max(BigDecimal.ZERO);
            discountCodeId = ((Number) coupon.get("id")).longValue();
        }

        BigDecimal total = subtotal.subtract(discount);

        String orderNumber = "VC-" + UUID.randomUUID()
            .toString().substring(0, 8).toUpperCase();

        Long orderId = jdbc.queryForObject("""
            INSERT INTO orders
            (order_number, user_id, discount_code_id, subtotal,
             discount_amount, total_amount, order_status, payment_status, shipping_address)
            VALUES (?, ?, ?, ?, ?, ?, 'PLACED', 'PENDING', ?)
            RETURNING id
            """, Long.class, orderNumber, userId, discountCodeId,
            subtotal, discount, total, address.trim());
        payments.createPending(orderId, total, paymentProvider.code());

        for (Map<String, Object> item : items) {
            Long productId = ((Number) item.get("product_id")).longValue();
            int quantity = ((Number) item.get("quantity")).intValue();
            BigDecimal price = (BigDecimal) item.get("price");

            int updated = jdbc.update("""
                UPDATE products
                SET stock_quantity = stock_quantity - ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND stock_quantity >= ?
                """, quantity, productId, quantity);

            if (updated != 1) {
                throw new IllegalArgumentException(
                    "Stock changed during checkout. Please try again."
                );
            }

            jdbc.update("""
                INSERT INTO order_items
                (order_id, product_id, product_name, product_sku,
                 unit_price, quantity, line_total)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, orderId, productId, item.get("name"),
                item.get("sku"), price, quantity,
                price.multiply(BigDecimal.valueOf(quantity)));
        }

        if (discountCodeId != null) {
            jdbc.update("""
                UPDATE discount_codes
                SET used_count = used_count + 1
                WHERE id = ?
                """, discountCodeId);
        }

        jdbc.update("DELETE FROM cart_items WHERE cart_id = ?", cartId);

        return Map.of(
            "message", "Order placed successfully.",
            "orderId", orderId,
            "orderNumber", orderNumber,
            "couponCode", couponCode == null ? "" : couponCode,
            "subtotal", subtotal,
            "discount", discount,
            "total", total,
            "status", "PLACED"
        );
    }

    public List<Map<String, Object>> getOrders(Long userId) {
        return jdbc.queryForList("""
            SELECT o.id, o.order_number, o.subtotal, o.discount_amount,
                   o.total_amount, o.order_status AS status, o.payment_status,
                   o.shipping_address, o.created_at, p.status AS payment_attempt_status,
                   p.provider AS payment_provider,
                   r.status AS refund_status
            FROM orders o
            LEFT JOIN payments p ON p.order_id = o.id
            LEFT JOIN refund_requests r ON r.order_id = o.id
            WHERE o.user_id = ?
            ORDER BY o.created_at DESC
            """, userId);
    }

    public Map<String, Object> getOrder(Long userId, Long orderId) {
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT o.id, o.order_number, o.subtotal, o.discount_amount,
                   o.total_amount, o.order_status AS status, o.payment_status,
                   o.shipping_address, o.created_at, p.status AS payment_attempt_status,
                   p.provider AS payment_provider,
                   r.status AS refund_status
            FROM orders o
            LEFT JOIN payments p ON p.order_id = o.id
            LEFT JOIN refund_requests r ON r.order_id = o.id
            WHERE o.id = ? AND o.user_id = ?
            """, orderId, userId);

        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Order not found.");
        }

        List<Map<String, Object>> items = jdbc.queryForList("""
            SELECT product_name, product_sku, unit_price,
                   quantity, line_total
            FROM order_items WHERE order_id = ?
            """, orderId);

        return Map.of("order", orders.get(0), "items", items);
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT id, order_status AS status, discount_code_id, payment_status
            FROM orders
            WHERE id = ? AND user_id = ?
            FOR UPDATE
            """, orderId, userId);

        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Order not found.");
        }

        Map<String, Object> order = orders.get(0);
        String status = (String) order.get("status");

        if (!"PLACED".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException(
                "Only placed orders can be cancelled."
            );
        }
        if ("PAID".equalsIgnoreCase(String.valueOf(order.get("payment_status")))) {
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

        Object discountId = order.get("discount_code_id");
        Long couponId = discountId == null ? null : ((Number) discountId).longValue();
        inventory.restoreOrder(orderId, couponId);
        jdbc.update("""
            UPDATE orders
            SET order_status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND user_id = ?
            """, orderId, userId);
    }
}