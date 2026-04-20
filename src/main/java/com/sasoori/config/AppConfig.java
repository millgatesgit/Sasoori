package com.sasoori.config;

import io.jsonwebtoken.Jwts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Centralised configuration loaded from environment variables.
 * Singleton — initialised once in AppContextListener.
 */
public class AppConfig {

    private static final Logger log = LogManager.getLogger(AppConfig.class);

    // ── Database ─────────────────────────────────────────────────────────
    public final String dbUrl;
    public final String dbUser;
    public final String dbPassword;
    public final int    dbPoolMax;
    public final int    dbPoolMinIdle;

    // ── JWT ───────────────────────────────────────────────────────────────
    public final PrivateKey jwtPrivateKey;
    public final PublicKey  jwtPublicKey;
    public final int        jwtAccessTtlMinutes;
    public final int        jwtRefreshTtlDays;

    // ── Google OAuth ──────────────────────────────────────────────────────
    public final String googleClientId;
    public final String googleClientSecret;
    public final String googleRedirectUri;

    // ── Dev mode ──────────────────────────────────────────────────────────
    public final boolean devMode;

    // ── CORS / Cookie ─────────────────────────────────────────────────────
    public final String  frontendUrl;
    public final boolean secureCookie;

    // ── Razorpay ──────────────────────────────────────────────────────────
    public final String razorpayKeyId;
    public final String razorpayKeySecret;
    public final String razorpayWebhookSecret;
    public final int    freeShippingThresholdPaise;
    public final int    shippingChargePaise;

    // ── WhatsApp ──────────────────────────────────────────────────────────
    public final String whatsappAccessToken;
    public final String whatsappPhoneNumberId;

    // ── Shiprocket ────────────────────────────────────────────────────────
    public final String shiprocketEmail;
    public final String shiprocketPassword;
    public final String shiprocketPickupLocation;

    // ── OTP / SMS ─────────────────────────────────────────────────────────
    public final String msg91AuthKey;
    public final String msg91TemplateId;
    public final String fast2smsApiKey;
    public final int    otpRateLimitCount;
    public final int    otpRateLimitWindowMinutes;

    // ── Singleton ─────────────────────────────────────────────────────────
    private static volatile AppConfig INSTANCE;

    private AppConfig() {
        this.dbUrl         = require("DB_URL");
        this.dbUser        = require("DB_USER");
        this.dbPassword    = require("DB_PASSWORD");
        this.dbPoolMax     = Integer.parseInt(env("DB_POOL_MAX",      "10"));
        this.dbPoolMinIdle = Integer.parseInt(env("DB_POOL_MIN_IDLE", "2"));

        this.googleClientId     = require("GOOGLE_CLIENT_ID");
        this.googleClientSecret = require("GOOGLE_CLIENT_SECRET");
        this.googleRedirectUri  = env("GOOGLE_REDIRECT_URI",
                                       "http://localhost:9090/api/v1/auth/google/callback");

        this.devMode      = Boolean.parseBoolean(env("DEV_MODE", "false"));
        if (this.devMode) log.warn("DEV_MODE=true — test login endpoint is enabled. Never use in production.");

        this.frontendUrl  = env("FRONTEND_URL", "http://localhost:5500");
        this.secureCookie = Boolean.parseBoolean(env("SECURE_COOKIE", "false"));
        if (!this.secureCookie) log.warn("SECURE_COOKIE=false — refresh token cookie sent over HTTP (dev only)");

        this.jwtAccessTtlMinutes = Integer.parseInt(env("JWT_ACCESS_TTL_MINUTES", "15"));
        this.jwtRefreshTtlDays   = Integer.parseInt(env("JWT_REFRESH_TTL_DAYS",   "7"));

        this.razorpayKeyId             = env("RAZORPAY_KEY_ID",         "");
        this.razorpayKeySecret         = env("RAZORPAY_KEY_SECRET",     "");
        this.razorpayWebhookSecret     = env("RAZORPAY_WEBHOOK_SECRET", "");
        this.freeShippingThresholdPaise = Integer.parseInt(env("FREE_SHIPPING_THRESHOLD_PAISE", "49900"));
        this.shippingChargePaise        = Integer.parseInt(env("SHIPPING_CHARGE_PAISE", "4900"));

        this.whatsappAccessToken   = env("WHATSAPP_ACCESS_TOKEN",    null);
        this.whatsappPhoneNumberId = env("WHATSAPP_PHONE_NUMBER_ID", null);

        this.shiprocketEmail           = env("SHIPROCKET_EMAIL",    "");
        this.shiprocketPassword        = env("SHIPROCKET_PASSWORD", "");
        this.shiprocketPickupLocation  = env("SHIPROCKET_PICKUP_LOCATION", "Primary");

        this.msg91AuthKey              = env("MSG91_AUTH_KEY",    "");
        this.msg91TemplateId           = env("MSG91_TEMPLATE_ID", "");
        this.fast2smsApiKey            = env("FAST2SMS_API_KEY",  "");
        this.otpRateLimitCount         = Integer.parseInt(env("OTP_RATE_LIMIT_COUNT",          "3"));
        this.otpRateLimitWindowMinutes = Integer.parseInt(env("OTP_RATE_LIMIT_WINDOW_MINUTES", "10"));

        KeyPair kp = loadOrGenerateKeyPair();
        this.jwtPrivateKey = kp.getPrivate();
        this.jwtPublicKey  = kp.getPublic();
    }

    public static AppConfig get() {
        if (INSTANCE == null) {
            synchronized (AppConfig.class) {
                if (INSTANCE == null) INSTANCE = new AppConfig();
            }
        }
        return INSTANCE;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) v = System.getProperty(key);
        return (v == null || v.isBlank()) ? defaultValue : v.trim();
    }

    private static String require(String key) {
        String v = env(key, null);
        if (v == null) throw new IllegalStateException("Required env var not set: " + key);
        return v;
    }

    private KeyPair loadOrGenerateKeyPair() {
        String privPem = env("JWT_PRIVATE_KEY", null);
        String pubPem  = env("JWT_PUBLIC_KEY",  null);

        if (privPem != null && pubPem != null) {
            try {
                KeyFactory kf = KeyFactory.getInstance("RSA");

                byte[] privBytes = Base64.getDecoder().decode(
                        privPem.replaceAll("-----[^-]+-----", "").replaceAll("\\s+", ""));
                byte[] pubBytes  = Base64.getDecoder().decode(
                        pubPem .replaceAll("-----[^-]+-----", "").replaceAll("\\s+", ""));

                PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
                PublicKey  pub  = kf.generatePublic(new X509EncodedKeySpec(pubBytes));

                log.info("JWT: loaded RSA keypair from environment variables");
                return new KeyPair(pub, priv);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load RSA keys from environment", e);
            }
        }

        log.warn("JWT_PRIVATE_KEY not set — generating ephemeral RSA-2048 keypair (dev only)");
        return Jwts.SIG.RS256.keyPair().build();
    }
}
