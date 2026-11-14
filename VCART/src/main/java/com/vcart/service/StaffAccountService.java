package com.vcart.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class StaffAccountService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public StaffAccountService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Map<String, Object>> list(Long ownerId) {
        requireOwner(ownerId);
        return jdbc.queryForList("""
            SELECT s.id, s.user_id, s.full_name, s.email, s.job_title, s.is_active,
                   u.is_active AS account_active
            FROM staff_members s
            LEFT JOIN users u ON u.id = s.user_id
            ORDER BY s.created_at DESC
            """);
    }

    @Transactional
    public Map<String, Object> create(Long ownerId, Map<String, Object> data) {
        requireOwner(ownerId);
        StaffDetails details = details(data);
        String password = Objects.toString(data.get("password"), "");
        validatePassword(password);

        List<Map<String, Object>> existingStaff = jdbc.queryForList("""
            SELECT id, user_id, is_active FROM staff_members
            WHERE LOWER(email) = LOWER(?) FOR UPDATE
            """, details.email());
        if (!existingStaff.isEmpty()) {
            Long staffId = number(existingStaff.get(0).get("id"));
            if (existingStaff.get(0).get("user_id") != null) {
                throw new IllegalArgumentException("A staff account already exists for this email.");
            }
            boolean active = Boolean.TRUE.equals(existingStaff.get(0).get("is_active"));
            Long accountId = createAccount(details, password, active);
            jdbc.update("UPDATE staff_members SET user_id = ? WHERE id = ?", accountId, staffId);
            return Map.of("message", "Login enabled for the existing staff record.", "staffId", staffId);
        }

        Long accountId = createAccount(details, password, true);
        Long staffId = jdbc.queryForObject("""
            INSERT INTO staff_members(full_name, email, job_title, user_id)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """, Long.class, details.fullName(), details.email(), details.jobTitle(), accountId);
        return Map.of("message", "Staff account created.", "staffId", staffId);
    }

    @Transactional
    public Map<String, Object> enableAccount(Long ownerId, Long staffId, Map<String, Object> data) {
        requireOwner(ownerId);
        String password = Objects.toString(data == null ? null : data.get("password"), "");
        validatePassword(password);

        List<Map<String, Object>> staff = jdbc.queryForList("""
            SELECT id, user_id, full_name, email, job_title, is_active
            FROM staff_members WHERE id = ? FOR UPDATE
            """, staffId);
        if (staff.isEmpty()) {
            throw new IllegalArgumentException("Staff record not found.");
        }
        Map<String, Object> record = staff.get(0);
        if (record.get("user_id") != null) {
            throw new IllegalArgumentException("A login account already exists for this staff record.");
        }
        StaffDetails details = new StaffDetails(
            (String) record.get("full_name"),
            ((String) record.get("email")).trim().toLowerCase(),
            (String) record.get("job_title")
        );
        Long accountId = createAccount(details, password,
            Boolean.TRUE.equals(record.get("is_active")));
        jdbc.update("UPDATE staff_members SET user_id = ? WHERE id = ?", accountId, staffId);
        return Map.of("message", "Staff login account created.", "staffId", staffId);
    }

    @Transactional
    public Map<String, Object> update(Long ownerId, Long staffId, Map<String, Object> data) {
        requireOwner(ownerId);
        StaffDetails details = details(data);
        List<Map<String, Object>> staff = jdbc.queryForList("""
            SELECT user_id FROM staff_members WHERE id = ? FOR UPDATE
            """, staffId);
        if (staff.isEmpty()) {
            throw new IllegalArgumentException("Staff record not found.");
        }
        Object accountId = staff.get(0).get("user_id");
        if (accountId != null) {
            jdbc.update("""
                UPDATE users SET full_name = ?, email = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND role = 'STAFF'
                """, details.fullName(), details.email(), accountId);
        }
        jdbc.update("""
            UPDATE staff_members SET full_name = ?, email = ?, job_title = ?
            WHERE id = ?
            """, details.fullName(), details.email(), details.jobTitle(), staffId);
        return Map.of("message", "Staff record updated.");
    }

    @Transactional
    public Map<String, Object> setActive(Long ownerId, Long staffId, boolean active) {
        requireOwner(ownerId);
        List<Map<String, Object>> staff = jdbc.queryForList("""
            SELECT user_id FROM staff_members WHERE id = ? FOR UPDATE
            """, staffId);
        if (staff.isEmpty()) {
            throw new IllegalArgumentException("Staff record not found.");
        }
        Object accountId = staff.get(0).get("user_id");
        jdbc.update("UPDATE staff_members SET is_active = ? WHERE id = ?", active, staffId);
        if (accountId != null) {
            jdbc.update("""
                UPDATE users SET is_active = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND role = 'STAFF'
                """, active, accountId);
        }
        return Map.of("message", active ? "Staff account activated." : "Staff account deactivated.");
    }

    @Transactional
    public Map<String, Object> resetPassword(Long ownerId, Long staffId, Map<String, Object> data) {
        requireOwner(ownerId);
        String password = Objects.toString(data == null ? null : data.get("password"), "");
        validatePassword(password);
        List<Long> accountIds = jdbc.query("""
            SELECT user_id FROM staff_members
            WHERE id = ? AND user_id IS NOT NULL AND is_active = TRUE
            FOR UPDATE
            """, (rs, row) -> rs.getLong("user_id"), staffId);
        if (accountIds.isEmpty()) {
            throw new IllegalArgumentException("Active staff login account not found.");
        }
        jdbc.update("""
            UPDATE users SET password_hash = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND role = 'STAFF' AND is_active = TRUE
            """, passwordEncoder.encode(password), accountIds.get(0));
        return Map.of("message", "Staff password reset.");
    }

    private Long createAccount(StaffDetails details, String password, boolean active) {
        Long existingAccount = jdbc.query("""
            SELECT id FROM users WHERE LOWER(email) = LOWER(?)
            """, rs -> rs.next() ? rs.getLong("id") : null, details.email());
        if (existingAccount != null) {
            throw new IllegalArgumentException("An account already uses this email.");
        }
        return jdbc.queryForObject("""
            INSERT INTO users(full_name, email, password_hash, role, is_active)
            VALUES (?, ?, ?, 'STAFF', ?)
            RETURNING id
            """, Long.class, details.fullName(), details.email(),
            passwordEncoder.encode(password), active);
    }

    private StaffDetails details(Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("Staff details are required.");
        }
        String name = Objects.toString(data.get("fullName"), "").trim();
        String email = Objects.toString(data.get("email"), "").trim().toLowerCase();
        String title = Objects.toString(data.get("jobTitle"), "Staff").trim();
        if (name.isBlank() || name.length() > 120 || email.length() > 255 ||
                !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$") ||
                title.isBlank() || title.length() > 80) {
            throw new IllegalArgumentException("Enter a valid staff name, email and job title.");
        }
        return new StaffDetails(name, email, title);
    }

    private static void validatePassword(String password) {
        if (password.length() < 8 ||
                password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Staff passwords must be 8-72 UTF-8 bytes.");
        }
    }

    private void requireOwner(Long ownerId) {
        List<String> roles = jdbc.query("""
            SELECT role FROM users WHERE id = ? AND is_active = TRUE
            """, (rs, row) -> rs.getString("role"), ownerId);
        if (roles.isEmpty() || !"ADMIN".equalsIgnoreCase(roles.get(0))) {
            throw new IllegalArgumentException("Administrator access required.");
        }
    }

    private static Long number(Object value) {
        return ((Number) value).longValue();
    }

    private record StaffDetails(String fullName, String email, String jobTitle) {}
}
