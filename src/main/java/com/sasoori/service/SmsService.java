package com.sasoori.service;

import com.sasoori.config.AppConfig;
import com.sasoori.exception.ApiException;
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
 * Sends OTP SMS via MSG91 (primary) with Fast2SMS as fallback.
 */
public class SmsService {

    private static final Logger log = LogManager.getLogger(SmsService.class);

    private static final String MSG91_OTP_URL = "https://api.msg91.com/api/v5/otp";
    private static final String FAST2SMS_URL  = "https://www.fast2sms.com/dev/bulkV2";

    private final AppConfig cfg;

    public SmsService(AppConfig cfg) {
        this.cfg = cfg;
    }

    /**
     * Sends the OTP to the given phone number.
     * Tries MSG91 first; falls back to Fast2SMS on failure.
     * throws ApiException 503 if both providers fail.
     */
    public void send(String phone, String otp) {
        Exception msg91Error = null;
        try {
            sendViaMSG91(phone, otp);
            log.info("SMS sent via MSG91 to {}", maskPhone(phone));
            return;
        } catch (Exception e) {
            msg91Error = e;
            log.warn("MSG91 failed for {} — trying Fast2SMS: {}", maskPhone(phone), e.getMessage());
        }

        try {
            sendViaFast2SMS(phone, otp);
            log.info("SMS sent via Fast2SMS (fallback) to {}", maskPhone(phone));
        } catch (Exception e) {
            log.error("Both SMS providers failed for {}: MSG91={}, Fast2SMS={}",
                    maskPhone(phone), msg91Error.getMessage(), e.getMessage());
            throw new ApiException(503, "SMS_DELIVERY_FAILED",
                    "Unable to send OTP. Please try again later.");
        }
    }

    // ── MSG91 ─────────────────────────────────────────────────────────────

    private void sendViaMSG91(String phone, String otp) throws IOException {
        // MSG91 v5 expects number without '+' prefix (e.g. 919876543210)
        String mobile = phone.startsWith("+") ? phone.substring(1) : phone;

        String urlStr = MSG91_OTP_URL
                + "?template_id=" + urlEncode(cfg.msg91TemplateId)
                + "&mobile="      + urlEncode(mobile)
                + "&otp="         + urlEncode(otp);

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("authkey",      cfg.msg91AuthKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write("{}".getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        try (InputStream is = (status >= 400) ? conn.getErrorStream() : conn.getInputStream()) {
            String body = is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
            if (status != 200 || !body.contains("\"success\"")) {
                throw new IOException("MSG91 returned status=" + status);
            }
        }
    }

    // ── Fast2SMS ──────────────────────────────────────────────────────────

    private void sendViaFast2SMS(String phone, String otp) throws IOException {
        // Fast2SMS expects 10-digit number without country code
        String number = phone.startsWith("+91") ? phone.substring(3)
                       : phone.startsWith("91") ? phone.substring(2)
                       : phone;

        String body = "route=otp"
                + "&variables_values=" + urlEncode(otp)
                + "&flash=0"
                + "&numbers=" + urlEncode(number);

        HttpURLConnection conn = (HttpURLConnection) new URL(FAST2SMS_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("authorization", cfg.fast2smsApiKey);
        conn.setRequestProperty("Content-Type",  "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("Fast2SMS returned status=" + status);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return phone.substring(0, phone.length() - 6) + "******"
               + phone.substring(phone.length() - 2);
    }
}
