package com.sasoori.service;

import com.google.gson.JsonObject;
import com.sasoori.config.AppConfig;
import com.sasoori.exception.ApiException;
import com.sasoori.util.JsonUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Handles the Google OAuth2 PKCE authorization code exchange and ID-token validation.
 *
 * <p>Token endpoint: {@code https://oauth2.googleapis.com/token}
 * <p>Tokeninfo validation: {@code https://oauth2.googleapis.com/tokeninfo?id_token=...}
 */
public class GoogleOAuthService {

    private static final Logger log = LogManager.getLogger(GoogleOAuthService.class);

    private static final String TOKEN_URL     = "https://oauth2.googleapis.com/token";
    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final AppConfig cfg;

    public GoogleOAuthService(AppConfig cfg) {
        this.cfg = cfg;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Exchanges a PKCE authorization code for a validated GoogleUser.
     * implements: AC-1, AC-2
     */
    public GoogleUser exchangeCode(String code, String codeVerifier) {
        String idToken = fetchIdToken(code, codeVerifier);
        return validateIdToken(idToken);
    }

    // ── Steps ─────────────────────────────────────────────────────────────

    /**
     * POST to Google token endpoint; returns raw id_token string.
     */
    private String fetchIdToken(String code, String codeVerifier) {
        String body = "code="          + urlEncode(code)
                + "&code_verifier="    + urlEncode(codeVerifier)
                + "&client_id="        + urlEncode(cfg.googleClientId)
                + "&client_secret="    + urlEncode(cfg.googleClientSecret)
                + "&redirect_uri="     + urlEncode(cfg.googleRedirectUri)
                + "&grant_type=authorization_code";

        try {
            String responseBody = post(TOKEN_URL, "application/x-www-form-urlencoded", body, null);
            JsonObject json = JsonUtil.parseObject(responseBody);
            if (!json.has("id_token")) {
                throw ApiException.badRequest("GOOGLE_NO_ID_TOKEN", "Google response missing id_token");
            }
            return json.get("id_token").getAsString();
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw ApiException.internal("Google token exchange request failed: " + e.getMessage());
        }
    }

    /**
     * Validates the id_token via Google's tokeninfo endpoint and extracts claims.
     */
    private GoogleUser validateIdToken(String idToken) {
        try {
            String responseBody = get(TOKENINFO_URL + urlEncode(idToken), null);
            JsonObject claims = JsonUtil.parseObject(responseBody);

            // Verify audience matches our client ID (confused-deputy prevention)
            String aud = claims.has("aud") ? claims.get("aud").getAsString() : "";
            if (!cfg.googleClientId.equals(aud)) {
                log.warn("Google id_token aud mismatch: expected={} got={}", cfg.googleClientId, aud);
                throw ApiException.unauthorized("Google token audience mismatch");
            }

            String sub     = getString(claims, "sub");
            String email   = getString(claims, "email");
            String name    = getStringOrNull(claims, "name");
            String picture = getStringOrNull(claims, "picture");

            if (sub == null || sub.isBlank()) {
                throw ApiException.unauthorized("Google token missing sub claim");
            }

            return new GoogleUser(sub, email, name, picture);
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw ApiException.internal("Google tokeninfo request failed: " + e.getMessage());
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────

    private static String post(String urlStr, String contentType, String body,
                                String authHeader) throws IOException {
        HttpURLConnection conn = openConnection(urlStr);
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("Content-Type", contentType);
        if (authHeader != null) conn.setRequestProperty("Authorization", authHeader);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            log.warn("HTTP POST {} returned {}", urlStr, status);
            throw new IOException("HTTP " + status);
        }
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String get(String urlStr, String authHeader) throws IOException {
        HttpURLConnection conn = openConnection(urlStr);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        if (authHeader != null) conn.setRequestProperty("Authorization", authHeader);

        int status = conn.getResponseCode();
        if (status != 200) {
            log.warn("HTTP GET {} returned {}", urlStr, status);
            throw new IOException("HTTP " + status);
        }
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private static HttpURLConnection openConnection(String urlStr) throws IOException {
        return (HttpURLConnection) new URL(urlStr).openConnection();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String getString(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsString() : null;
    }

    private static String getStringOrNull(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString() : null;
    }

    // ── Value type ────────────────────────────────────────────────────────

    public record GoogleUser(String sub, String email, String name, String picture) {}
}
