package com.vcart.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CategoryService {
    private final JdbcTemplate jdbc;

    public CategoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> getActiveCategories() {
        return jdbc.queryForList("""
            SELECT id, name, description
            FROM categories
            WHERE is_active = TRUE
            ORDER BY name
            """);
    }
}
