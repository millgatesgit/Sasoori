package com.sasoori.filter;

import com.sasoori.exception.ApiException;
import com.sasoori.service.TokenService;
import com.sasoori.util.JsonUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;

/**
 * Validates Bearer JWT on every request to /api/v1/* except public paths.
 * Sets {@code userId} and {@code userRole} as request attributes for downstream servlets.
 */
@WebFilter(urlPatterns = "/api/v1/*", filterName = "JWTAuthFilter")
public class JWTAuthFilter implements Filter {

    private static final Logger log = LogManager.getLogger(JWTAuthFilter.class);

    /**
     * Paths that do NOT require authentication.
     * Matching is prefix/exact against the path after /api/v1.
     */
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/auth/google",
            "/auth/otp",
            "/auth/refresh",
            "/auth/test-login",
            "/products",
            "/categories",
            "/payments/webhook",
            "/config"
    );

    private TokenService tokenService;

    @Override
    public void init(FilterConfig cfg) {
        tokenService = (TokenService) cfg.getServletContext().getAttribute("tokenService");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  req  = (HttpServletRequest)  request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = apiSubPath(req);

        // Allow public paths without a token
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Extract Bearer token
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            sendError(resp, 401, "MISSING_TOKEN", "Authorization header required");
            return;
        }
        String token = header.substring(7).trim();

        // Validate token
        Claims claims;
        try {
            claims = tokenService.parseAccessToken(token);
        } catch (ApiException e) {
            sendError(resp, e.getHttpStatus(), e.getErrorCode(), e.getMessage());
            return;
        }

        // Attach claims to request
        req.setAttribute("userId",   claims.getSubject());
        req.setAttribute("userRole", claims.get("role", String.class));

        // Admin path guard
        if (path.startsWith("/admin") && !"ADMIN".equals(claims.get("role", String.class))) {
            sendError(resp, 403, "FORBIDDEN", "Admin access required");
            return;
        }

        chain.doFilter(request, response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static boolean isPublic(String path) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    /** Returns the path segment after /api/v1 */
    private static String apiSubPath(HttpServletRequest req) {
        String uri = req.getRequestURI();
        int idx = uri.indexOf("/api/v1");
        if (idx < 0) return uri;
        String after = uri.substring(idx + "/api/v1".length());
        return after.isEmpty() ? "/" : after;
    }

    private static void sendError(HttpServletResponse resp, int status,
                                   String code, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.write(JsonUtil.toJson(Map.of(
                    "success", false,
                    "error", Map.of("code", code, "message", message))));
        }
    }
}
