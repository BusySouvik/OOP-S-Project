package com.vcart.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    public Map<String, Object> register(Map<String, String> data) {
        if (data == null) {
            throw new IllegalArgumentException("Registration details are required.");
        }
        String name = data.get("fullName");
        String email = data.get("email");
        String password = data.get("password");
        String phone = data.get("phone");
        if (phone == null) phone = "";

        if (name == null || name.isBlank() || name.trim().length() > 120 ||
            email == null || email.isBlank() ||
            email.trim().length() > 255 ||
            password == null || password.length() < 8 ||
            password.getBytes(StandardCharsets.UTF_8).length > 72 ||
            phone.length() > 20) {
            throw new IllegalArgumentException(
                "Enter a name (up to 120 characters), valid email, phone (up to 20 characters) and password (8-72 UTF-8 bytes)."
            );
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }

        String normalizedEmail = email.trim().toLowerCase();

        if (jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                Integer.class, normalizedEmail) > 0) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        String hash = encoder.encode(password);

        Long id = jdbc.queryForObject("""
            INSERT INTO users(full_name, email, password_hash, phone, role)
            VALUES (?, ?, ?, ?, 'CUSTOMER')
            RETURNING id
            """, Long.class, name.trim(), normalizedEmail, hash, phone);

        return Map.of("message", "Registration successful.", "userId", id);
    }

    public Map<String, Object> login(Map<String, String> data) {
        return login(data, null);
    }

    public Map<String, Object> login(Map<String, String> data, String expectedRole) {
        if (data == null) {
            throw new IllegalArgumentException("Email and password are required.");
        }
        String email = data.get("email");
        String password = data.get("password");

        if (email == null || password == null) {
            throw new IllegalArgumentException("Email and password are required.");
        }

        var users = jdbc.queryForList("""
            SELECT id, full_name, email, password_hash, role, is_active
            FROM users WHERE email = ?
            """, email.trim().toLowerCase());

        if (users.isEmpty()) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        Map<String, Object> user = users.get(0);
        String actualRole = String.valueOf(user.get("role")).toUpperCase();
        if (expectedRole != null && !expectedRole.equals(actualRole)) {
            throw new IllegalArgumentException("These credentials are not registered for this sign-in portal.");
        }

        if (!Boolean.TRUE.equals(user.get("is_active")) ||
            !encoder.matches(password, (String) user.get("password_hash"))) {
            throw new IllegalArgumentException("Invalid email or password.");
        }
        if ("STAFF".equals(actualRole)) {
            Integer activeStaff = jdbc.queryForObject("""
                SELECT COUNT(*) FROM staff_members
                WHERE user_id = ? AND is_active = TRUE
                """, Integer.class, user.get("id"));
            if (activeStaff == null || activeStaff != 1) {
                throw new IllegalArgumentException("Invalid email or password.");
            }
        }

        return Map.of(
            "message", "Login successful.",
            "userId", user.get("id"),
            "fullName", user.get("full_name"),
            "email", user.get("email"),
            "role", user.get("role")
        );
    }

    public Map<String, Object> currentUser(String email) {
        var users = jdbc.queryForList("""
            SELECT id, full_name, email, role
            FROM users
            WHERE email = ? AND is_active = TRUE
            """, email);

        if (users.isEmpty()) {
            throw new IllegalArgumentException("User account not found.");
        }

        return users.get(0);
    }
}