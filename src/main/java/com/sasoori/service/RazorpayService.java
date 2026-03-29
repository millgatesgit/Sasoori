package com.sasoori.service;

import com.google.gson.JsonObject;
import com.sasoori.config.AppConfig;
import com.sasoori.exception.ApiException;
import com.sasoori.util.JsonUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Razorpay API integration: create orders, verify payment signatures, verify webhooks.
 */
public class RazorpayService {

    private static final Logger log = LogManager.getLogger(RazorpayService.class);

    private static final String RAZORPAY_ORDERS_URL = "https://api.razorpay.com/v1/orders";

    private final AppConfig cfg;
    private final String    basicAuth;

    public RazorpayService(AppConfig cfg) {
        this.cfg = cfg;
        // Basic Auth: Base64(keyId:keySecret)
        String credentials = cfg.razorpayKeyId + ":" + cfg.razorpayKeySecret;
        this.basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    // ── Create order ──────────────────────────────────────────────────────

    /**
     * Creates a Razorpay order and returns the Razorpay order_id.
     * implements: AC-orders-1
     */
    public String createOrder(int amountPaise, String currency, String receiptId) {
        String body = JsonUtil.toJson(Map.of(
                "amount",   amountPaise,
                "currency", currency,
                "receipt",  receiptId));

        try {
            String responseBody = post(RAZORPAY_ORDERS_URL, body, basicAuth);
            JsonObject json = JsonUtil.parseObject(responseBody);
            if (!json.has("id")) {
                throw ApiException.internal("Razorpay response missing order id");
            }
            String razorpayOrderId = json.get("id").getAsString();
            log.info("Razorpay order created: {}", razorpayOrderId);
            return razorpayOrderId;
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw ApiException.internal("Payment gateway unreachable: " + e.getMessage());
        }
    }

    // ── Signature verification ────────────────────────────────────────────

    /**
     * Verifies the payment signature returned by Razorpay checkout.
     * HMAC-SHA256(razorpayOrderId + "|" + razorpayPaymentId, keySecret)
     */
    public boolean verifyPaymentSignature(String razorpayOrderId,
                                           String razorpayPaymentId,
                                           String signature) {
        String message = razorpayOrderId + "|" + razorpayPaymentId;
        String expected = hmacSha256(message, cfg.razorpayKeySecret);
        return expected.equals(signature);
    }

    /**
     * Verifies the webhook signature from X-Razorpay-Signature header.
     * HMAC-SHA256(rawPayload, webhookSecret)
     */
    public boolean verifyWebhookSignature(String rawPayload, String webhookSignature) {
        if (webhookSignature == null || webhookSignature.isBlank()) return false;
        String expected = hmacSha256(rawPayload, cfg.razorpayWebhookSecret);
        return expected.equals(webhookSignature);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────

    private static String post(String urlStr, String body, String authHeader) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(20_000);
        conn.setRequestProperty("Content-Type", "application/json");
        if (authHeader != null) conn.setRequestProperty("Authorization", authHeader);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            log.error("Razorpay order creation failed: status={}", status);
            throw new IOException("HTTP " + status);
        }
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ── HMAC helper ───────────────────────────────────────────────────────

    private static String hmacSha256(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }
}
