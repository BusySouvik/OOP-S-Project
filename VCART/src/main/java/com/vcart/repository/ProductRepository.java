package com.vcart.repository;

import com.vcart.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Product> productRowMapper = (rs, rowNum) -> {
        Product product = new Product(
            rs.getLong("id"),
            rs.getObject("category_id", Long.class),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getString("sku"),
            rs.getString("description"),
            rs.getBigDecimal("price"),
            rs.getInt("stock_quantity"),
            rs.getString("image_url"),
            rs.getBoolean("is_active")
        );
        product.setCategoryName(rs.getString("category_name"));
        return product;
    };

    public List<Product> findAllActive() {
        String sql = """
            SELECT p.*, c.name AS category_name
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id AND c.is_active = TRUE
            WHERE p.is_active = TRUE
            ORDER BY p.id DESC
            """;

        return jdbcTemplate.query(sql, productRowMapper);
    }

    public Optional<Product> findById(Long id) {
        String sql = """
            SELECT p.*, c.name AS category_name
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id AND c.is_active = TRUE
            WHERE p.id = ? AND p.is_active = TRUE
            """;

        return jdbcTemplate.query(sql, productRowMapper, id)
                .stream()
                .findFirst();
    }

    public List<Product> findCatalog(String keyword, Long categoryId,
                                     BigDecimal minimumPrice,
                                     BigDecimal maximumPrice,
                                     Boolean inStock, String sort,
                                     Integer limit, Integer offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT p.*, c.name AS category_name
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id AND c.is_active = TRUE
            WHERE p.is_active = TRUE
            """);
        List<Object> parameters = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append("""
                 AND (p.name ILIKE ? OR COALESCE(p.description, '') ILIKE ?
                      OR p.sku ILIKE ? OR COALESCE(c.name, '') ILIKE ?)
                """);
            String pattern = "%" + keyword.trim() + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        if (categoryId != null) {
            sql.append(" AND c.id = ?");
            parameters.add(categoryId);
        }
        if (minimumPrice != null) {
            sql.append(" AND p.price >= ?");
            parameters.add(minimumPrice);
        }
        if (maximumPrice != null) {
            sql.append(" AND p.price <= ?");
            parameters.add(maximumPrice);
        }
        if (Boolean.TRUE.equals(inStock)) {
            sql.append(" AND p.stock_quantity > 0");
        } else if (Boolean.FALSE.equals(inStock)) {
            sql.append(" AND p.stock_quantity = 0");
        }

        if ("relevance".equals(sort) && keyword != null && !keyword.isBlank()) {
            sql.append(" ORDER BY CASE WHEN p.name ILIKE ? THEN 0 ELSE 1 END, p.name, p.id DESC");
            parameters.add(keyword.trim() + "%");
        } else {
            sql.append(switch (sort) {
                case "price_asc" -> " ORDER BY p.price ASC, p.id DESC";
                case "price_desc" -> " ORDER BY p.price DESC, p.id DESC";
                case "name_asc" -> " ORDER BY p.name ASC, p.id DESC";
                case "newest" -> " ORDER BY p.created_at DESC, p.id DESC";
                default -> " ORDER BY p.id DESC";
            });
        }

        if (limit != null) {
            sql.append(" LIMIT ? OFFSET ?");
            parameters.add(limit);
            parameters.add(offset);
        }
        return jdbcTemplate.query(sql.toString(), productRowMapper, parameters.toArray());
    }

    public int save(Product product) {
        String sql = """
            INSERT INTO products
            (category_id, name, slug, sku, description, price,
             stock_quantity, image_url, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        return jdbcTemplate.update(
            sql,
            product.getCategoryId(),
            product.getName(),
            product.getSlug(),
            product.getSku(),
            product.getDescription(),
            product.getPrice(),
            product.getStockQuantity(),
            product.getImageUrl(),
            product.getIsActive() != null ? product.getIsActive() : true
        );
    }

    public int update(Product product) {
        String sql = """
            UPDATE products
            SET category_id = ?, name = ?, slug = ?, sku = ?,
                description = ?, price = ?, stock_quantity = ?,
                image_url = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        return jdbcTemplate.update(
            sql,
            product.getCategoryId(),
            product.getName(),
            product.getSlug(),
            product.getSku(),
            product.getDescription(),
            product.getPrice(),
            product.getStockQuantity(),
            product.getImageUrl(),
            product.getIsActive(),
            product.getId()
        );
    }

    public int updateStock(Long productId, int quantity) {
        String sql = """
            UPDATE products
            SET stock_quantity = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND ? >= 0
            """;

        return jdbcTemplate.update(sql, quantity, productId, quantity);
    }

    public int delete(Long id) {
        String sql = """
            UPDATE products
            SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        return jdbcTemplate.update(sql, id);
    }
}