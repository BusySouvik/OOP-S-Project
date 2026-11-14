package com.vcart.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DiscountService {

    private final JdbcTemplate jdbc;

    public DiscountService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> getAll() {
        return jdbc.queryForList("""
            SELECT id, code,
                   discount_type AS type,
                   discount_value AS value,
                   minimum_order_amount, maximum_discount,
                   usage_limit, used_count, starts_at, expires_at, is_active
            FROM discount_codes
            ORDER BY id DESC
            """);
    }

    public Map<String, Object> validate(String code, BigDecimal subtotal) {
        if (code == null || code.isBlank() || subtotal == null
                || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid code and subtotal are required.");
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT *,
                   (starts_at IS NULL OR starts_at <= CURRENT_TIMESTAMP) AS started,
                   (expires_at IS NULL OR expires_at >= CURRENT_TIMESTAMP) AS not_expired
            FROM discount_codes
            WHERE UPPER(code) = UPPER(?) AND is_active = TRUE
            """, code.trim());

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Invalid or inactive discount code.");
        }

        Map<String, Object> coupon = rows.get(0);

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
        Object limitValue = coupon.get("usage_limit");

        if (limitValue != null && used >= ((Number) limitValue).intValue()) {
            throw new IllegalArgumentException("Discount code usage limit reached.");
        }

        String type = coupon.get("discount_type").toString();
        BigDecimal value = (BigDecimal) coupon.get("discount_value");
        BigDecimal discount;

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

        return Map.of(
                "code", coupon.get("code"),
                "subtotal", subtotal,
                "discount", discount,
                "total", subtotal.subtract(discount)
        );
    }

    public Map<String, Object> create(Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("Discount details are required.");
        }
        String code = Objects.toString(data.get("code"), "").trim().toUpperCase();
        String type = Objects.toString(data.get("type"), "").trim().toUpperCase();

        if (data.get("value") == null) {
            throw new IllegalArgumentException("Discount value is required.");
        }

        BigDecimal value = new BigDecimal(data.get("value").toString());

        if (!code.matches("[A-Z0-9_-]{1,50}") ||
                value.compareTo(BigDecimal.ZERO) <= 0 ||
                value.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Valid code and value are required.");
        }

        if (!type.equals("PERCENTAGE") && !type.equals("FIXED")) {
            throw new IllegalArgumentException("Type must be PERCENTAGE or FIXED.");
        }

        if (type.equals("PERCENTAGE")
                && value.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Percentage cannot exceed 100.");
        }

        BigDecimal minimum = data.get("minimumOrderAmount") == null
                ? BigDecimal.ZERO
                : new BigDecimal(data.get("minimumOrderAmount").toString());

        BigDecimal maximum = data.get("maximumDiscount") == null
                ? null
                : new BigDecimal(data.get("maximumDiscount").toString());

        Integer usageLimit = data.get("usageLimit") == null
                ? null
                : Integer.valueOf(data.get("usageLimit").toString());
        if (minimum.compareTo(BigDecimal.ZERO) < 0 ||
                minimum.compareTo(new BigDecimal("9999999999.99")) > 0 ||
                (maximum != null && (maximum.compareTo(BigDecimal.ZERO) < 0 ||
                    maximum.compareTo(new BigDecimal("9999999999.99")) > 0)) ||
                (usageLimit != null && usageLimit < 1)) {
            throw new IllegalArgumentException("Minimum, maximum and usage limit values are invalid.");
        }

        jdbc.update("""
            INSERT INTO discount_codes
            (code, discount_type, discount_value, minimum_order_amount,
             maximum_discount, usage_limit, is_active)
            VALUES (?, ?, ?, ?, ?, ?, TRUE)
            """, code, type, value, minimum, maximum, usageLimit);

        return Map.of("message", "Discount code created.", "code", code);
    }

    public void deactivate(String code) {
        int changed = jdbc.update("""
            UPDATE discount_codes
            SET is_active = FALSE
            WHERE UPPER(code) = UPPER(?)
            """, code);

        if (changed == 0) {
            throw new IllegalArgumentException("Discount code not found.");
        }
    }
}