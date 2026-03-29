package com.sasoori.dao;

import com.sasoori.model.OtpVerification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Data-access for the {@code otp_verifications} table.
 */
public class OtpVerificationDao {

    private static final Logger log = LogManager.getLogger(OtpVerificationDao.class);

    private final DataSource ds;

    public OtpVerificationDao(DataSource ds) {
        this.ds = ds;
    }

    // ── Queries ───────────────────────────────────────────────────────────

    /**
     * Count OTPs sent for this phone within the rolling window.
     * Used for rate limiting.
     */
    public int countRecentByPhone(String phone, int windowMinutes) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM otp_verifications
            WHERE phone = ?
              AND created_at > NOW() - (? * INTERVAL '1 minute')
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setInt(2, windowMinutes);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Insert a new OTP record.
     */
    public void create(String phone, String otpHash,
                       OffsetDateTime expiresAt) throws SQLException {
        String sql = """
            INSERT INTO otp_verifications (phone, otp_hash, expires_at)
            VALUES (?, ?, ?)
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setString(2, otpHash);
            ps.setTimestamp(3, Timestamp.from(expiresAt.toInstant()));
            ps.executeUpdate();
        }
    }

    /**
     * Returns the most recent non-used OTP record for the phone,
     * regardless of expiry (expiry checked in service layer for precise errors).
     */
    public Optional<OtpVerification> findLatestActiveByPhone(String phone) throws SQLException {
        String sql = """
            SELECT id, phone, otp_hash, attempts, is_used, expires_at, created_at
            FROM otp_verifications
            WHERE phone = ?
              AND is_used = FALSE
            ORDER BY created_at DESC
            LIMIT 1
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public void incrementAttempts(long id) throws SQLException {
        String sql = "UPDATE otp_verifications SET attempts = attempts + 1 WHERE id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public void markUsed(long id) throws SQLException {
        String sql = "UPDATE otp_verifications SET is_used = TRUE WHERE id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    // ── Mapper ────────────────────────────────────────────────────────────

    private static OtpVerification map(ResultSet rs) throws SQLException {
        OtpVerification o = new OtpVerification();
        o.setId(rs.getLong("id"));
        o.setPhone(rs.getString("phone"));
        o.setOtpHash(rs.getString("otp_hash"));
        o.setAttempts(rs.getInt("attempts"));
        o.setUsed(rs.getBoolean("is_used"));
        Timestamp exp = rs.getTimestamp("expires_at");
        if (exp != null) o.setExpiresAt(exp.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) o.setCreatedAt(ca.toInstant().atOffset(ZoneOffset.UTC));
        return o;
    }
}
