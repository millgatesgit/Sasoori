package com.sasoori.servlet.payment;

import com.google.gson.JsonObject;
import com.sasoori.dao.OrderDao;
import com.sasoori.dao.PaymentDao;
import com.sasoori.dto.PaymentVerifyRequest;
import com.sasoori.exception.ApiException;
import com.sasoori.service.OrderService;
import com.sasoori.service.RazorpayService;
import com.sasoori.servlet.BaseServlet;
import com.sasoori.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;

/**
 * Payment endpoints:
 * <pre>
 *   POST /api/v1/payments/verify   — verify client-side payment (requires auth)
 *   POST /api/v1/payments/webhook  — Razorpay server webhook (public, HMAC-verified)
 * </pre>
 */
@WebServlet(urlPatterns = "/api/v1/payments/*")
public class PaymentServlet extends BaseServlet {

    private static final Logger log = LogManager.getLogger(PaymentServlet.class);

    private OrderService   orderService;
    private RazorpayService razorpayService;
    private PaymentDao     paymentDao;
    private OrderDao       orderDao;

    @Override
    public void init() {
        orderService    = (OrderService)    getServletContext().getAttribute("orderService");
        razorpayService = (RazorpayService) getServletContext().getAttribute("razorpayService");
        paymentDao      = (PaymentDao)      getServletContext().getAttribute("paymentDao");
        orderDao        = (OrderDao)        getServletContext().getAttribute("orderDao");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = subPath(req);
        // Webhook is fully handled inline (reads raw body first)
        if ("/webhook".equals(path)) {
            handleWebhook(req, resp);
            return;
        }
        handle(req, resp, () -> {
            if ("/verify".equals(path)) {
                handleVerify(req, resp);
            } else {
                throw ApiException.notFound("Unknown payment endpoint: " + path);
            }
        });
    }

    // ── Verify (client-side) ──────────────────────────────────────────────

    private void handleVerify(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {
        String userId = requireUserId(req);
        PaymentVerifyRequest body = parseBody(req, PaymentVerifyRequest.class);

        if (body.orderId == null || body.orderId.isBlank())
            throw ApiException.badRequest("MISSING_ORDER_ID", "orderId is required");
        if (body.razorpayPaymentId == null || body.razorpayPaymentId.isBlank())
            throw ApiException.badRequest("MISSING_PAYMENT_ID", "razorpayPaymentId is required");
        if (body.razorpaySignature == null || body.razorpaySignature.isBlank())
            throw ApiException.badRequest("MISSING_SIGNATURE", "razorpaySignature is required");

        orderService.verifyPayment(userId, body.orderId,
                body.razorpayPaymentId, body.razorpaySignature);

        sendSuccess(resp, Map.of("message", "Payment verified successfully"));
    }

    // ── Webhook (server-side) ─────────────────────────────────────────────

    /**
     * Handles Razorpay server-to-server webhook.
     * Always returns 200 to prevent Razorpay retries (errors logged only).
     */
    private void handleWebhook(HttpServletRequest req, HttpServletResponse resp) {
        try {
            byte[] rawBytes = req.getInputStream().readAllBytes();
            String rawBody  = new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);
            String signature = req.getHeader("X-Razorpay-Signature");

            // Verify HMAC signature
            if (!razorpayService.verifyWebhookSignature(rawBody, signature)) {
                log.warn("Webhook: invalid signature — ignoring");
                resp.setStatus(400);
                return;
            }

            JsonObject payload = JsonUtil.parseObject(rawBody);
            String event = payload.has("event") ? payload.get("event").getAsString() : "";
            log.info("Webhook received: event={}", event);

            switch (event) {
                case "payment.captured" -> handleWebhookCapture(payload, rawBody);
                case "payment.failed"   -> handleWebhookFailed(payload, rawBody);
                default -> log.debug("Webhook: unhandled event={}", event);
            }

            sendSuccess(resp, Map.of("status", "ok"));

        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage(), e);
            // Still return 200 — Razorpay must not retry indefinitely
            try { sendSuccess(resp, Map.of("status", "error")); }
            catch (IOException ioe) { log.error("Webhook response write failed", ioe); }
        }
    }

    private void handleWebhookCapture(JsonObject payload, String rawBody) throws Exception {
        JsonObject entity = payload
                .getAsJsonObject("payload")
                .getAsJsonObject("payment")
                .getAsJsonObject("entity");

        String razorpayOrderId = entity.get("order_id").getAsString();
        String paymentId       = entity.get("id").getAsString();
        String method          = entity.has("method") ? entity.get("method").getAsString() : null;

        // Find our order by razorpay_order_id
        String orderId = findOrderIdByRazorpayOrderId(razorpayOrderId);
        if (orderId == null) {
            log.warn("Webhook capture: no order found for razorpay_order_id={}", razorpayOrderId);
            return;
        }

        paymentDao.saveWebhookPayload(orderId, rawBody, "CAPTURED");
        if (method != null) paymentDao.updateMethod(orderId, method);
        orderDao.updateStatus(orderId, "PAID");
        log.info("Webhook: payment captured orderId={} paymentId={}", orderId, paymentId);
    }

    private void handleWebhookFailed(JsonObject payload, String rawBody) throws Exception {
        JsonObject entity = payload
                .getAsJsonObject("payload")
                .getAsJsonObject("payment")
                .getAsJsonObject("entity");

        String razorpayOrderId = entity.get("order_id").getAsString();
        String orderId = findOrderIdByRazorpayOrderId(razorpayOrderId);
        if (orderId == null) {
            log.warn("Webhook failed: no order found for razorpay_order_id={}", razorpayOrderId);
            return;
        }

        paymentDao.saveWebhookPayload(orderId, rawBody, "FAILED");
        // Keep order status as PENDING so user can retry payment
        log.info("Webhook: payment failed orderId={}", orderId);
    }

    /** Look up our internal orderId from the Razorpay order_id via the payments table. */
    private String findOrderIdByRazorpayOrderId(String razorpayOrderId) throws java.sql.SQLException {
        // Reuse paymentDao — add a convenience lookup method inline using the datasource
        // via the existing getRazorpayOrderId reverse — we need a new query
        // For now use a direct SQL via getServletContext datasource attribute is not available here;
        // delegate to paymentDao helper (added below as findOrderIdByRazorpayOrderId)
        return paymentDao.findOrderIdByRazorpayOrderId(razorpayOrderId);
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private static String subPath(HttpServletRequest req) {
        String uri   = req.getRequestURI();
        String ctx   = req.getContextPath();
        String after = uri.substring(ctx.length());
        int    idx   = after.indexOf("/api/v1/payments");
        return idx >= 0 ? after.substring(idx + "/api/v1/payments".length()) : after;
    }
}
