package com.sasoori.dao;

import com.sasoori.model.RefreshTokenRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Data-access for the {@code refresh_tokens} table.
 */
public class RefreshTokenDao {

    private static final Logger log = LogManager.getLogger(RefreshTokenDao.class);

    private final DataSource ds;

    public RefreshTokenDao(DataSource ds) {
        this.ds = ds;
    }

    // ── Create ────────────────────────────────────────────────────────────

    /**
     * Insert a new refresh token record.
     */
    public void create(String userId, String tokenHash,
                       OffsetDateTime expiresAt,
                       String userAgent, String ipAddress) throws SQLException {
        // implements: AC-3
        String sql = """
            INSERT INTO refresh_tokens (user_id, token_hash, expires_at, user_agent, ip_address)
            VALUES (?::uuid, ?, ?, ?, ?::inet)
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, tokenHash);
            ps.setTimestamp(3, Timestamp.from(expiresAt.toInstant()));
            ps.setString(4, userAgent);
            ps.setString(5, ipAddress);
            ps.executeUpdate();
        }
    }

    // ── Lookup ────────────────────────────────────────────────────────────

    /**
     * Find an active (not revoked, not expired) token by its SHA-256 hash.
     */
    public Optional<RefreshTokenRecord> findActiveByHash(String tokenHash) throws SQLException {
        // implements: AC-5
        String sql = """
            SELECT id,user_id,token_hash,is_revoked,user_agent,ip_address,expires_at,created_at
            FROM refresh_tokens
            WHERE token_hash=?
              AND is_revoked = FALSE
              AND expires_at > NOW()
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    /**
     * Find ANY (possibly revoked) token by hash — used for reuse detection.
     */
    public Optional<RefreshTokenRecord> findByHash(String tokenHash) throws SQLException {
        String sql = """
            SELECT id,user_id,token_hash,is_revoked,user_agent,ip_address,expires_at,created_at
            FROM refresh_tokens WHERE token_hash=?
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    // ── Revoke ────────────────────────────────────────────────────────────

    public void revokeByHash(String tokenHash) throws SQLException {
        String sql = "UPDATE refresh_tokens SET is_revoked=TRUE WHERE token_hash=?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            ps.executeUpdate();
        }
    }

    /**
     * Revoke all tokens for a user (reuse-detection or logout-all-devices).
     * implements: AC-6
     */
    public void revokeAllForUser(String userId) throws SQLException {
        String sql = "UPDATE refresh_tokens SET is_revoked=TRUE WHERE user_id=?::uuid";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            int rows = ps.executeUpdate();
            log.warn("Revoked {} refresh tokens for userId={} (reuse detection)", rows, userId);
        }
    }

    // ── Mapper ────────────────────────────────────────────────────────────

    private static RefreshTokenRecord map(ResultSet rs) throws SQLException {
        RefreshTokenRecord r = new RefreshTokenRecord();
        r.setId(rs.getString("id"));
        r.setUserId(rs.getString("user_id"));
        r.setTokenHash(rs.getString("token_hash"));
        r.setRevoked(rs.getBoolean("is_revoked"));
        r.setUserAgent(rs.getString("user_agent"));
        r.setIpAddress(rs.getString("ip_address"));
        Timestamp exp = rs.getTimestamp("expires_at");
        if (exp != null) r.setExpiresAt(exp.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) r.setCreatedAt(ca.toInstant().atOffset(ZoneOffset.UTC));
        return r;
    }
}
