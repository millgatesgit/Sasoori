package com.sasoori.service;

import com.sasoori.config.AppConfig;
import com.sasoori.util.JsonUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Sends WhatsApp notifications via Meta Cloud API (WhatsApp Business).
 *
 * <p>Required env vars:
 * <ul>
 *   <li>WHATSAPP_ACCESS_TOKEN — permanent system user token
 *   <li>WHATSAPP_PHONE_NUMBER_ID — sender phone number ID
 * </ul>
 *
 * <p>Template names must be pre-approved in Meta Business Manager.
 */
public class WhatsAppService {

    private static final Logger log = LogManager.getLogger(WhatsAppService.class);

    private final AppConfig cfg;

    public WhatsAppService(AppConfig cfg) {
        this.cfg = cfg;
    }

    // ── Notification templates ────────────────────────────────────────────

    /**
     * Sends order confirmation WhatsApp message.
     * Template: order_confirmed
     * Variables: {{1}}=customerName, {{2}}=orderId, {{3}}=totalAmount
     */
    public void sendOrderConfirmation(String phone, String customerName,
                                       String orderId, String totalAmount) {
        sendTemplate(phone, "order_confirmed", "en",
                List.of(customerName, orderId, totalAmount));
    }

    /**
     * Sends payment successful notification.
     * Template: payment_received
     * Variables: {{1}}=customerName, {{2}}=orderId, {{3}}=amount
     */
    public void sendPaymentConfirmation(String phone, String customerName,
                                         String orderId, String amount) {
        sendTemplate(phone, "payment_received", "en",
                List.of(customerName, orderId, amount));
    }

    /**
     * Sends shipment dispatched notification with tracking info.
     * Template: order_shipped
     * Variables: {{1}}=customerName, {{2}}=orderId, {{3}}=awbCode, {{4}}=courierName
     */
    public void sendShipmentNotification(String phone, String customerName,
                                          String orderId, String awbCode,
                                          String courierName) {
        sendTemplate(phone, "order_shipped", "en",
                List.of(customerName, orderId, awbCode, courierName));
    }

    /**
     * Sends OTP via WhatsApp as fallback (when SMS fails).
     * Template: otp_verification
     * Variables: {{1}}=otp
     */
    public void sendOtp(String phone, String otp) {
        sendTemplate(phone, "otp_verification", "en", List.of(otp));
    }

    // ── Core send ─────────────────────────────────────────────────────────

    private void sendTemplate(String phone, String templateName,
                               String language, List<String> params) {
        if (cfg.whatsappAccessToken == null || cfg.whatsappPhoneNumberId == null) {
            log.warn("WhatsApp not configured — skipping notification to {}", maskPhone(phone));
            return;
        }

        // Build component parameters
        List<Map<String, String>> parameters = params.stream()
                .map(v -> Map.of("type", "text", "text", v))
                .toList();

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to",                normalisePhone(phone),
                "type",              "template",
                "template", Map.of(
                        "name",     templateName,
                        "language", Map.of("code", language),
                        "components", List.of(Map.of(
                                "type",       "body",
                                "parameters", parameters
                        ))
                )
        );

        String urlStr = "https://graph.facebook.com/v19.0/"
                + cfg.whatsappPhoneNumberId + "/messages";

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("Authorization", "Bearer " + cfg.whatsappAccessToken);
            conn.setRequestProperty("Content-Type",  "application/json");
            conn.setDoOutput(true);

            byte[] bodyBytes = JsonUtil.toJson(body).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int status = conn.getResponseCode();
            // consume response stream
            InputStream is = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
            if (is != null) is.close();

            if (status == 200) {
                log.info("WhatsApp {} sent to {}", templateName, maskPhone(phone));
            } else {
                log.warn("WhatsApp send failed: template={} status={}", templateName, status);
            }
        } catch (IOException e) {
            log.warn("WhatsApp send error: template={} error={}", templateName, e.getMessage());
            // Non-fatal — notifications are best-effort
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Ensures phone is in E.164 format without '+' (Meta API expects this). */
    private static String normalisePhone(String phone) {
        if (phone == null) return "";
        return phone.startsWith("+") ? phone.substring(1) : phone;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return phone.substring(0, phone.length() - 6) + "******"
               + phone.substring(phone.length() - 2);
    }
}
