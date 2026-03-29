package com.sasoori.service;

import com.sasoori.config.AppConfig;
import com.sasoori.dao.RefreshTokenDao;
import com.sasoori.exception.ApiException;
import com.sasoori.model.RefreshTokenRecord;
import com.sasoori.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues and validates RS256 JWTs; manages refresh token lifecycle.
 */
public class TokenService {

    private static final Logger log = LogManager.getLogger(TokenService.class);

    private static final String ISSUER     = "sasoori";
    private static final String CLAIM_ROLE = "role";

    private final AppConfig       cfg;
    private final RefreshTokenDao rtDao;
    private final SecureRandom    rng = new SecureRandom();

    public TokenService(AppConfig cfg, RefreshTokenDao rtDao) {
        this.cfg   = cfg;
        this.rtDao = rtDao;
    }

    // ── Access Token ──────────────────────────────────────────────────────

    /**
     * Issues a short-lived RS256 access JWT.
     * implements: AC-3
     */
    public String issueAccessToken(User user) {
        Date now    = new Date();
        Date expiry = Date.from(OffsetDateTime.now(ZoneOffset.UTC)
                .plusMinutes(cfg.jwtAccessTtlMinutes).toInstant());

        return Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getId())
                .claim(CLAIM_ROLE, user.getRole())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(cfg.jwtPrivateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Parses and validates an access token.
     * Returns Claims on success; throws ApiException on invalid/expired.
     * implements: AC-7
     */
    public Claims parseAccessToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(cfg.jwtPublicKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw ApiException.unauthorized("Invalid or expired access token");
        }
    }

    // ── Refresh Token ─────────────────────────────────────────────────────

    /**
     * Generates a secure random refresh token, stores its SHA-256 hash in DB,
     * and returns the raw token (to be set as HttpOnly cookie).
     * implements: AC-3
     */
    public String issueRefreshToken(User user,
                                     String userAgent,
                                     String ipAddress) throws SQLException {
        String rawToken = generateRawToken();
        String hash     = sha256(rawToken);
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(cfg.jwtRefreshTtlDays);
        rtDao.create(user.getId(), hash, expiresAt, userAgent, ipAddress);
        return rawToken;
    }

    /**
     * Validates the incoming refresh token, revokes it, issues a new one.
     * Reuse detection: if token is already revoked → revoke ALL tokens for that
     * user (session theft countermeasure) and throw 401.
     * implements: AC-5, AC-6
     *
     * @return RotationResult carrying userId and the new raw refresh token
     */
    public RotationResult rotateRefreshToken(String rawToken,
                                              String userAgent,
                                              String ipAddress) throws SQLException {
        String hash = sha256(rawToken);

        // Check if ANY record exists for this hash (catches reuse of revoked tokens)
        Optional<RefreshTokenRecord> anyRecord = rtDao.findByHash(hash);

        if (anyRecord.isPresent() && anyRecord.get().isRevoked()) {
            // Token reuse — possible theft, revoke all sessions
            rtDao.revokeAllForUser(anyRecord.get().getUserId());
            throw ApiException.unauthorized("Refresh token reuse detected — all sessions revoked");
        }

        // Fetch active (non-revoked, non-expired) record
        RefreshTokenRecord active = rtDao.findActiveByHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired refresh token"));

        // Revoke the current token
        rtDao.revokeByHash(hash);

        // Issue replacement
        String newRaw = generateRawToken();
        String newHash = sha256(newRaw);
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(cfg.jwtRefreshTtlDays);
        rtDao.create(active.getUserId(), newHash, expiresAt, userAgent, ipAddress);

        return new RotationResult(active.getUserId(), newRaw);
    }

    /**
     * Revokes a specific refresh token (single-device logout).
     */
    public void revokeRefreshToken(String rawToken) throws SQLException {
        rtDao.revokeByHash(sha256(rawToken));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        rng.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ── Result type ───────────────────────────────────────────────────────

    public record RotationResult(String userId, String newRawToken) {}
}
