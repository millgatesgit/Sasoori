package com.sasoori.service;

import com.sasoori.config.AppConfig;
import com.sasoori.dao.OtpVerificationDao;
import com.sasoori.dao.UserDao;
import com.sasoori.dto.AuthResponse;
import com.sasoori.dto.UserResponse;
import com.sasoori.exception.ApiException;
import com.sasoori.model.OtpVerification;
import com.sasoori.model.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Orchestrates OTP generation, rate-limiting, storage, and verification.
 */
public class OtpService {

    private static final Logger  log    = LogManager.getLogger(OtpService.class);
    private static final Pattern PHONE  = Pattern.compile("^\\+91[6-9]\\d{9}$");
    private static final Pattern DIGITS = Pattern.compile("\\d{6}");

    private final AppConfig          cfg;
    private final OtpVerificationDao otpDao;
    private final UserDao            userDao;
    private final TokenService       tokenService;
    private final SmsService         smsService;
    private final SecureRandom       rng = new SecureRandom();

    public OtpService(AppConfig cfg,
                      OtpVerificationDao otpDao,
                      UserDao userDao,
                      TokenService tokenService,
                      SmsService smsService) {
        this.cfg          = cfg;
        this.otpDao       = otpDao;
        this.userDao      = userDao;
        this.tokenService = tokenService;
        this.smsService   = smsService;
    }

    // ── Send OTP ──────────────────────────────────────────────────────────

    /**
     * Validates the phone, enforces rate-limit, generates OTP, persists hash, sends SMS.
     * implements: AC-OTP-1 through AC-OTP-5
     */
    public Map<String, Object> sendOtp(String phone) throws SQLException {
        validatePhone(phone);

        int recent = otpDao.countRecentByPhone(phone, cfg.otpRateLimitWindowMinutes);
        if (recent >= cfg.otpRateLimitCount) {
            throw new ApiException(429, "RATE_LIMIT",
                    "Too many OTP requests. Please try again later.");
        }

        String otp      = generateOtp();
        String otpHash  = TokenService.sha256(otp);
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);

        otpDao.create(phone, otpHash, expiresAt);
        smsService.send(phone, otp);  // throws 503 SMS_DELIVERY_FAILED on total failure

        log.info("OTP sent to {}", SmsService.maskPhone(phone));
        return Map.of("message", "OTP sent successfully", "expiresIn", 300);
    }

    // ── Verify OTP ────────────────────────────────────────────────────────

    /**
     * Validates the OTP against the stored hash; on success upserts user and issues JWT.
     * implements: AC-OTP-6 through AC-OTP-14
     */
    public OtpAuthResult verifyOtp(String phone, String otp,
                                    String userAgent, String ipAddress) throws SQLException {
        validatePhone(phone);

        if (!DIGITS.matcher(otp).matches()) {
            throw ApiException.badRequest("INVALID_OTP_FORMAT", "OTP must be exactly 6 digits");
        }

        OtpVerification record = otpDao.findLatestActiveByPhone(phone)
                .orElseThrow(() -> ApiException.notFound(
                        "No pending OTP found for this number. Please request a new one."));

        // Check attempt limit BEFORE comparing hash (prevents brute-force)
        if (record.getAttempts() >= 3) {
            throw ApiException.badRequest("TOO_MANY_ATTEMPTS",
                    "Too many incorrect attempts. Please request a new OTP.");
        }

        // Check expiry
        if (record.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw ApiException.badRequest("OTP_EXPIRED",
                    "OTP has expired. Please request a new one.");
        }

        // Compare hashes
        String inputHash = TokenService.sha256(otp);
        if (!inputHash.equals(record.getOtpHash())) {
            otpDao.incrementAttempts(record.getId());
            throw ApiException.badRequest("INVALID_OTP",
                    "Incorrect OTP. Please check and try again.");
        }

        // Consume the OTP
        otpDao.markUsed(record.getId());

        // Upsert user by phone
        User user = userDao.upsertPhoneUser(phone);

        if (!user.isActive()) {
            throw ApiException.forbidden("Your account has been suspended");
        }

        String accessToken  = tokenService.issueAccessToken(user);
        String refreshToken = tokenService.issueRefreshToken(user, userAgent, ipAddress);

        log.info("OTP login success userId={} phone={}", user.getId(), SmsService.maskPhone(phone));
        return new OtpAuthResult(accessToken, refreshToken, user);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static void validatePhone(String phone) {
        if (!PHONE.matcher(phone).matches()) {
            throw ApiException.badRequest("INVALID_PHONE",
                    "Enter a valid 10-digit Indian mobile number (e.g. +919876543210)");
        }
    }

    private String generateOtp() {
        int n = rng.nextInt(900_000) + 100_000;  // range [100000, 999999]
        return String.valueOf(n);
    }

    // ── Result type ───────────────────────────────────────────────────────

    public record OtpAuthResult(String accessToken, String refreshToken, User user) {}
}
