package com.sasoori.dao;

import com.sasoori.model.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

/**
 * Data-access for the {@code users} table.
 */
public class UserDao {

    private static final Logger log = LogManager.getLogger(UserDao.class);

    private final DataSource ds;

    public UserDao(DataSource ds) {
        this.ds = ds;
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public Optional<User> findById(String id) throws SQLException {
        String sql = "SELECT id,google_sub,email,phone,name,picture_url,role,is_active,password_hash," +
                     "created_at,updated_at FROM users WHERE id=?::uuid";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<User> findByGoogleSub(String googleSub) throws SQLException {
        String sql = "SELECT id,google_sub,email,phone,name,picture_url,role,is_active,password_hash," +
                     "created_at,updated_at FROM users WHERE google_sub=?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, googleSub);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    /**
     * INSERT … ON CONFLICT (google_sub) DO UPDATE — upserts the Google user
     * and always returns the current (post-upsert) row.
     */
    public User upsertGoogleUser(String googleSub, String email,
                                  String name, String pictureUrl) throws SQLException {
        // implements: AC-2
        String sql = """
            INSERT INTO users (google_sub, email, name, picture_url)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (google_sub) DO UPDATE
                SET email       = EXCLUDED.email,
                    name        = EXCLUDED.name,
                    picture_url = EXCLUDED.picture_url,
                    updated_at  = NOW()
            RETURNING id,google_sub,email,phone,name,picture_url,role,is_active,password_hash,created_at,updated_at
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, googleSub);
            ps.setString(2, email);
            ps.setString(3, name);
            ps.setString(4, pictureUrl);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        throw new IllegalStateException("Upsert returned no row for google_sub=" + googleSub);
    }

    /**
     * INSERT … ON CONFLICT (phone) DO UPDATE — upserts the phone-auth user.
     * Preserves google_sub and all other fields if the user already exists.
     */
    public User upsertPhoneUser(String phone) throws SQLException {
        // implements: AC-OTP-10, AC-OTP-14
        String sql = """
            INSERT INTO users (phone)
            VALUES (?)
            ON CONFLICT (phone) DO UPDATE
                SET updated_at = NOW()
            RETURNING id,google_sub,email,phone,name,picture_url,role,is_active,password_hash,created_at,updated_at
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        throw new IllegalStateException("Upsert returned no row for phone=" + phone);
    }

    /**
     * Upserts the fixed dev-mode test user (test@sasoori.dev).
     * Only called when DEV_MODE=true — never used in production.
     */
    public User upsertTestUser() throws SQLException {
        // google_sub is set to satisfy the users_identity constraint (google_sub NOT NULL OR phone NOT NULL)
        String sql = """
            INSERT INTO users (google_sub, email, name, role)
            VALUES ('dev-test-user', 'test@sasoori.dev', 'Test User', 'CUSTOMER')
            ON CONFLICT (google_sub) DO UPDATE
                SET updated_at = NOW()
            RETURNING id,google_sub,email,phone,name,picture_url,role,is_active,password_hash,created_at,updated_at
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return map(rs);
        }
        throw new IllegalStateException("upsertTestUser returned no row");
    }

    // ── Admin queries ─────────────────────────────────────────────────────

    public record UserPage(java.util.List<User> users, int total) {}

    public UserPage findAll(int page, int size) throws SQLException {
        size = Math.min(size, 50);
        int offset = (Math.max(page, 1) - 1) * size;
        String countSql = "SELECT COUNT(*) FROM users";
        String dataSql  = "SELECT id,google_sub,email,phone,name,picture_url,role,is_active,password_hash," +
                          "created_at,updated_at FROM users ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection c = ds.getConnection()) {
            int total;
            try (PreparedStatement ps = c.prepareStatement(countSql);
                 ResultSet rs = ps.executeQuery()) {
                rs.next(); total = rs.getInt(1);
            }
            java.util.List<User> users = new java.util.ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(dataSql)) {
                ps.setInt(1, size); ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) users.add(map(rs));
                }
            }
            return new UserPage(users, total);
        }
    }

    /** Updates name and phone for the given user; returns the refreshed row. */
    public User updateProfile(String id, String name, String phone) throws SQLException {
        String sql = """
            UPDATE users SET name=?, phone=?, updated_at=NOW()
            WHERE id=?::uuid
            RETURNING id,google_sub,email,phone,name,picture_url,role,is_active,password_hash,created_at,updated_at
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            if (phone != null && !phone.isBlank()) ps.setString(2, phone.trim());
            else ps.setNull(2, java.sql.Types.VARCHAR);
            ps.setString(3, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        throw new IllegalStateException("updateProfile returned no row for id=" + id);
    }

    public void setActive(String id, boolean active) throws SQLException {
        String sql = "UPDATE users SET is_active=?, updated_at=NOW() WHERE id=?::uuid";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, active); ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    public void setRole(String id, String role) throws SQLException {
        if (!"CUSTOMER".equals(role) && !"ADMIN".equals(role))
            throw new IllegalArgumentException("Invalid role: " + role);
        String sql = "UPDATE users SET role=?, updated_at=NOW() WHERE id=?::uuid";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, role); ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    // ── Mapper ────────────────────────────────────────────────────────────

    // ── Password-auth queries ─────────────────────────────────────────────

    /** Finds a user by email — includes password_hash for auth verification. */
    public Optional<User> findByEmail(String email) throws SQLException {
        String sql = "SELECT id,google_sub,email,phone,name,picture_url,role,is_active,password_hash," +
                     "created_at,updated_at FROM users WHERE email=?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email.toLowerCase().trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    /** Creates a new email+password user. Throws on duplicate email (SQL unique constraint). */
    public User createPasswordUser(String email, String name, String passwordHash) throws SQLException {
        String sql = """
            INSERT INTO users (email, name, password_hash)
            VALUES (?, ?, ?)
            RETURNING id,google_sub,email,phone,name,picture_url,role,is_active,password_hash,created_at,updated_at
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email.toLowerCase().trim());
            ps.setString(2, name.trim());
            ps.setString(3, passwordHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        throw new IllegalStateException("createPasswordUser returned no row for email=" + email);
    }

    // ── Mapper ────────────────────────────────────────────────────────────

    private static User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getString("id"));
        u.setGoogleSub(rs.getString("google_sub"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setName(rs.getString("name"));
        u.setPictureUrl(rs.getString("picture_url"));
        u.setRole(rs.getString("role"));
        u.setActive(rs.getBoolean("is_active"));
        u.setPasswordHash(rs.getString("password_hash"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) u.setCreatedAt(ca.toInstant().atOffset(java.time.ZoneOffset.UTC));
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) u.setUpdatedAt(ua.toInstant().atOffset(java.time.ZoneOffset.UTC));
        return u;
    }
}
