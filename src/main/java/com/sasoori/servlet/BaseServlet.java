package com.sasoori.servlet;

import com.sasoori.exception.ApiException;
import com.sasoori.util.JsonUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Base servlet providing:
 * <ul>
 *   <li>Uniform JSON response helpers</li>
 *   <li>Centralised exception-to-JSON mapping</li>
 *   <li>Wrapper {@code handle} method to avoid per-servlet try-catch</li>
 * </ul>
 */
public abstract class BaseServlet extends HttpServlet {

    private static final Logger log = LogManager.getLogger(BaseServlet.class);

    // ── Response helpers ──────────────────────────────────────────────────

    protected void sendSuccess(HttpServletResponse resp, int status, Object data)
            throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.write(JsonUtil.toJson(Map.of("success", true, "data", data)));
        }
    }

    protected void sendSuccess(HttpServletResponse resp, Object data) throws IOException {
        sendSuccess(resp, 200, data);
    }

    protected void sendError(HttpServletResponse resp, int status,
                              String code, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.write(JsonUtil.toJson(Map.of(
                    "success", false,
                    "error", Map.of("code", code, "message", message))));
        }
    }

    // ── Request helpers ───────────────────────────────────────────────────

    /**
     * Reads the full request body as a string.
     */
    protected String readBody(HttpServletRequest req) throws IOException {
        return new String(req.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Reads and parses the request body as the given type.
     */
    protected <T> T parseBody(HttpServletRequest req, Class<T> type) throws IOException {
        T obj = JsonUtil.fromJson(readBody(req), type);
        if (obj == null) throw new ApiException(400, "INVALID_BODY", "Request body is required");
        return obj;
    }

    /**
     * Retrieves the userId set by JWTAuthFilter.
     */
    protected String requireUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw ApiException.unauthorized("Authentication required");
        return id.toString();
    }

    protected String getUserRole(HttpServletRequest req) {
        Object role = req.getAttribute("userRole");
        return role != null ? role.toString() : "CUSTOMER";
    }

    // ── Exception routing ─────────────────────────────────────────────────

    /**
     * Wraps a servlet handler call, converts exceptions to JSON error responses.
     * Usage: {@code handle(req, resp, () -> doSomething(req, resp));}
     */
    protected void handle(HttpServletRequest req, HttpServletResponse resp,
                           ThrowingRunnable action) throws IOException {
        try {
            action.run();
        } catch (ApiException e) {
            log.debug("ApiException {}: {}", e.getHttpStatus(), e.getMessage());
            sendError(resp, e.getHttpStatus(), e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unhandled exception in {}: {}", req.getRequestURI(), e.getMessage(), e);
            sendError(resp, 500, "INTERNAL_ERROR", "An unexpected error occurred");
        }
    }

    @FunctionalInterface
    protected interface ThrowingRunnable {
        void run() throws Exception;
    }
}
