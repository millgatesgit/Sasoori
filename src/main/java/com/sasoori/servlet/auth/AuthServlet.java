package com.sasoori.servlet.auth;

import com.sasoori.config.AppConfig;
import com.sasoori.dao.RefreshTokenDao;
import com.sasoori.dao.UserDao;
import com.sasoori.dto.AuthResponse;
import com.sasoori.dto.GoogleAuthRequest;
import com.sasoori.dto.OtpSendRequest;
import com.sasoori.dto.OtpVerifyRequest;
import com.sasoori.dto.UserResponse;
import com.sasoori.exception.ApiException;
import com.sasoori.model.User;
import com.sasoori.servlet.BaseServlet;
import com.sasoori.service.GoogleOAuthService;
import com.sasoori.service.OtpService;
import com.sasoori.service.TokenService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Auth endpoints:
 * <pre>
 *   POST /api/v1/auth/google   — exchange Google PKCE code
 *   POST /api/v1/auth/refresh  — rotate refresh token
 *   POST /api/v1/auth/logout   — revoke refresh token
 *   GET  /api/v1/auth/me       — return current user
 * </pre>
 */
@WebServlet(urlPatterns = "/api/v1/auth/*")
public class AuthServlet extends BaseServlet {

    private static final Logger log = LogManager.getLogger(AuthServlet.class);

    static final String REFRESH_COOKIE = "sas_refresh";

    private UserDao            userDao;
    private RefreshTokenDao    rtDao;
    private TokenService       tokenService;
    private GoogleOAuthService googleOAuth;
    private OtpService         otpService;

    // Injected by AppContextListener via ServletContext attributes
    @Override
    public void init() {
        userDao      = (UserDao)            getServletContext().getAttribute("userDao");
        rtDao        = (RefreshTokenDao)    getServletContext().getAttribute("refreshTokenDao");
        tokenService = (TokenService)       getServletContext().getAttribute("tokenService");
        googleOAuth  = (GoogleOAuthService) getServletContext().getAttribute("googleOAuthService");
        otpService   = (OtpService)         getServletContext().getAttribute("otpService");
    }

    // ── Routing ───────────────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = subPath(req);
        handle(req, resp, () -> {
            switch (path) {
                case "/google"      -> handleGoogleLogin(req, resp);
                case "/refresh"     -> handleRefresh(req, resp);
                case "/logout"      -> handleLogout(req, resp);
                case "/otp/send"    -> handleOtpSend(req, resp);
                case "/otp/verify"  -> handleOtpVerify(req, resp);
                case "/test-login"  -> handleTestLogin(req, resp);
                default             -> throw ApiException.notFound("Unknown auth endpoint: " + path);
            }
        });
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = subPath(req);
        handle(req, resp, () -> {
            if ("/me".equals(path)) {
                handleMe(req, resp);
            } else {
                throw ApiException.notFound("Unknown auth endpoint: " + path);
            }
        });
    }

    // ── Handlers ──────────────────────────────────────────────────────────

    /**
     * POST /api/v1/auth/google
     * Body: { code, codeVerifier, state }
     * implements: AC-1, AC-2, AC-3, AC-4
     */
    private void handleGoogleLogin(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        GoogleAuthRequest body = parseBody(req, GoogleAuthRequest.class);
        if (body.code == null || body.code.isBlank())
            throw ApiException.badRequest("MISSING_CODE", "Authorization code is required");
        if (body.codeVerifier == null || body.codeVerifier.isBlank())
            throw ApiException.badRequest("MISSING_CODE_VERIFIER", "code_verifier is required");

        // Exchange code with Google and validate id_token
        GoogleOAuthService.GoogleUser gUser = googleOAuth.exchangeCode(body.code, body.codeVerifier);

        // Upsert user in DB
        User user = userDao.upsertGoogleUser(gUser.sub(), gUser.email(), gUser.name(), gUser.picture());

        if (!user.isActive())
            throw ApiException.forbidden("Your account has been suspended");

        // Issue tokens
        String accessToken  = tokenService.issueAccessToken(user);
        String refreshToken = tokenService.issueRefreshToken(user,
                req.getHeader("User-Agent"), getClientIp(req));

        setRefreshCookie(resp, refreshToken);
        log.info("Google login success userId={}", user.getId());
        sendSuccess(resp, 200, new AuthResponse(accessToken, new UserResponse(user)));
    }

    /**
     * POST /api/v1/auth/refresh
     * Reads refresh token from HttpOnly cookie; returns new access token + rotated cookie.
     * implements: AC-5, AC-6
     */
    private void handleRefresh(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        String rawToken = extractRefreshCookie(req)
                .orElseThrow(() -> ApiException.unauthorized("No refresh token cookie"));

        TokenService.RotationResult rotation = tokenService.rotateRefreshToken(
                rawToken, req.getHeader("User-Agent"), getClientIp(req));

        User user = userDao.findById(rotation.userId())
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        if (!user.isActive())
            throw ApiException.forbidden("Account suspended");

        String newAccessToken = tokenService.issueAccessToken(user);
        setRefreshCookie(resp, rotation.newRawToken());
        sendSuccess(resp, new AuthResponse(newAccessToken, new UserResponse(user)));
    }

    /**
     * POST /api/v1/auth/logout
     * implements: AC-8
     */
    private void handleLogout(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        extractRefreshCookie(req).ifPresent(raw -> {
            try { tokenService.revokeRefreshToken(raw); }
            catch (Exception e) { log.warn("Logout: revoke failed silently", e); }
        });
        clearRefreshCookie(resp);
        sendSuccess(resp, java.util.Map.of("message", "Logged out successfully"));
    }

    /**
     * GET /api/v1/auth/me
     * Requires valid access token (checked by JWTAuthFilter).
     * implements: AC-9
     */
    private void handleMe(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        String userId = requireUserId(req);
        User user = userDao.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        sendSuccess(resp, new UserResponse(user));
    }

    /**
     * POST /api/v1/auth/test-login
     * Dev-only shortcut — bypasses Google OAuth and OTP. Returns real JWT + refresh cookie
     * for a fixed test user (test@sasoori.dev). Only works when DEV_MODE=true.
     */
    private void handleTestLogin(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        if (!AppConfig.get().devMode) {
            throw ApiException.notFound("Unknown auth endpoint: /test-login");
        }

        User user = userDao.upsertTestUser();
        if (!user.isActive())
            throw ApiException.forbidden("Test user account is inactive");

        String accessToken  = tokenService.issueAccessToken(user);
        String refreshToken = tokenService.issueRefreshToken(user,
                req.getHeader("User-Agent"), getClientIp(req));

        setRefreshCookie(resp, refreshToken);
        log.info("Dev test login: userId={}", user.getId());
        sendSuccess(resp, 200, new AuthResponse(accessToken, new UserResponse(user)));
    }

    // ── OTP Handlers ─────────────────────────────────────────────────────

    /**
     * POST /api/v1/auth/otp/send
     * implements: AC-OTP-1 through AC-OTP-5
     */
    private void handleOtpSend(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        OtpSendRequest body = parseBody(req, OtpSendRequest.class);
        if (body.phone == null || body.phone.isBlank())
            throw ApiException.badRequest("MISSING_PHONE", "phone is required");
        Object result = otpService.sendOtp(body.phone.trim());
        sendSuccess(resp, result);
    }

    /**
     * POST /api/v1/auth/otp/verify
     * implements: AC-OTP-6 through AC-OTP-14
     */
    private void handleOtpVerify(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        OtpVerifyRequest body = parseBody(req, OtpVerifyRequest.class);
        if (body.phone == null || body.phone.isBlank())
            throw ApiException.badRequest("MISSING_PHONE", "phone is required");
        if (body.otp == null || body.otp.isBlank())
            throw ApiException.badRequest("MISSING_OTP", "otp is required");

        OtpService.OtpAuthResult result = otpService.verifyOtp(
                body.phone.trim(), body.otp.trim(),
                req.getHeader("User-Agent"), getClientIp(req));

        setRefreshCookie(resp, result.refreshToken());
        sendSuccess(resp, 200, new AuthResponse(result.accessToken(), new UserResponse(result.user())));
    }

    // ── Cookie helpers ────────────────────────────────────────────────────

    private void setRefreshCookie(HttpServletResponse resp, String rawToken) {
        // Build Set-Cookie header manually for full SameSite=Strict support
        // (Servlet API Cookie class doesn't support SameSite attribute)
        boolean secure = Boolean.parseBoolean(
                getServletContext().getInitParameter("secureCookie"));
        int maxAge = 7 * 24 * 60 * 60; // 7 days in seconds

        String cookieHeader = REFRESH_COOKIE + "=" + rawToken
                + "; Path=/api/v1/auth"
                + "; HttpOnly"
                + "; Max-Age=" + maxAge
                + "; SameSite=Strict"
                + (secure ? "; Secure" : "");
        resp.addHeader("Set-Cookie", cookieHeader);
    }

    private void clearRefreshCookie(HttpServletResponse resp) {
        resp.addHeader("Set-Cookie",
                REFRESH_COOKIE + "=; Path=/api/v1/auth; HttpOnly; Max-Age=0; SameSite=Strict");
    }

    private Optional<String> extractRefreshCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> REFRESH_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    // ── Misc helpers ──────────────────────────────────────────────────────

    private static String subPath(HttpServletRequest req) {
        String uri     = req.getRequestURI();
        String context = req.getContextPath();
        // Strip /api/v1/auth prefix → returns e.g. "/google", "/refresh"
        String after = uri.substring(context.length());
        // after = /api/v1/auth/google → sub = /google
        int idx = after.indexOf("/api/v1/auth");
        return idx >= 0 ? after.substring(idx + "/api/v1/auth".length()) : after;
    }

    private static String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        String ip = (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
        // Strip brackets from IPv6 addresses like [0:0:0:0:0:0:0:1] → 0:0:0:0:0:0:0:1
        if (ip != null && ip.startsWith("[") && ip.endsWith("]")) {
            ip = ip.substring(1, ip.length() - 1);
        }
        return ip;
    }
}
