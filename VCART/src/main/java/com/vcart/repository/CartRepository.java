package com.vcart.repository;

import com.vcart.model.CartItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class CartRepository {
    private final JdbcTemplate jdbc;

    public CartRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long getOrCreateCart(Long userId) {
        jdbc.update("""
            INSERT INTO carts(user_id) VALUES (?)
            ON CONFLICT(user_id) DO NOTHING
            """, userId);

        return jdbc.queryForObject(
            "SELECT id FROM carts WHERE user_id = ?",
            Long.class, userId
        );
    }

    public void lockCart(Long cartId) {
        jdbc.queryForObject(
            "SELECT id FROM carts WHERE id = ? FOR UPDATE",
            Long.class, cartId
        );
    }

    public List<CartItem> findItems(Long cartId) {
        return jdbc.query("""
            SELECT ci.id, p.id AS product_id, p.name, p.image_url, p.price,
                   ci.quantity, p.stock_quantity
            FROM cart_items ci
            JOIN products p ON p.id = ci.product_id
            WHERE ci.cart_id = ?
            ORDER BY ci.id
            """,
            (rs, row) -> new CartItem(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getString("name"),
                rs.getString("image_url"),
                rs.getBigDecimal("price"),
                rs.getInt("quantity"),
                rs.getInt("stock_quantity")
            ), cartId);
    }

    public Integer getStock(Long productId) {
        return jdbc.queryForObject(
            "SELECT stock_quantity FROM products WHERE id = ? AND is_active = TRUE",
            Integer.class, productId
        );
    }

    public int addItem(Long cartId, Long productId, int quantity) {
        return jdbc.update("""
            INSERT INTO cart_items(cart_id, product_id, quantity)
            SELECT ?, id, ?
            FROM products
            WHERE id = ? AND is_active = TRUE
              AND stock_quantity >= ?
            ON CONFLICT(cart_id, product_id)
            DO UPDATE SET quantity = cart_items.quantity + EXCLUDED.quantity
            WHERE cart_items.quantity + EXCLUDED.quantity <=
                  (SELECT stock_quantity FROM products WHERE id = ?)
            """, cartId, quantity, productId, quantity, productId);
    }

    public int updateQuantity(Long cartId, Long itemId, int quantity) {
        return jdbc.update("""
            UPDATE cart_items ci
            SET quantity = ?
            FROM products p
            WHERE ci.product_id = p.id
              AND ci.id = ? AND ci.cart_id = ?
              AND p.stock_quantity >= ?
            """, quantity, itemId, cartId, quantity);
    }

    public int removeItem(Long cartId, Long itemId) {
        return jdbc.update(
            "DELETE FROM cart_items WHERE id = ? AND cart_id = ?",
            itemId, cartId
        );
    }

    public void clearCart(Long cartId) {
        jdbc.update("DELETE FROM cart_items WHERE cart_id = ?", cartId);
    }
}