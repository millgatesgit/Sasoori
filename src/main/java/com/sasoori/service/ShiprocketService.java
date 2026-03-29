package com.sasoori.service;

import com.google.gson.JsonObject;
import com.sasoori.config.AppConfig;
import com.sasoori.exception.ApiException;
import com.sasoori.model.Order;
import com.sasoori.model.OrderItem;
import com.sasoori.util.JsonUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shiprocket API integration for shipment creation and tracking.
 *
 * <p>Auth: POST /auth/login → bearer token (valid 24 h, cached).
 * <p>Create shipment: POST /orders/create/adhoc
 * <p>Track: GET /courier/track/awb/{awb}
 */
public class ShiprocketService {

    private static final Logger log = LogManager.getLogger(ShiprocketService.class);

    private static final String BASE_URL  = "https://apiv2.shiprocket.in/v1/external";
    private static final String LOGIN_URL = BASE_URL + "/auth/login";

    private final AppConfig cfg;

    /** Cached bearer token + expiry epoch-seconds. */
    private final AtomicReference<String> cachedToken    = new AtomicReference<>();
    private final AtomicLong              tokenExpiresAt = new AtomicLong(0);

    public ShiprocketService(AppConfig cfg) {
        this.cfg = cfg;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Creates a Shiprocket order for a paid Sasoori order.
     * Returns the Shiprocket shipment_id (stored in shipments table).
     */
    public String createShipment(Order order, com.sasoori.model.Address shippingAddr) {
        String token = getToken();

        // Build items array
        List<Map<String, Object>> items = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                Map<String, Object> i = new HashMap<>();
                i.put("name",          item.getProductName());
                i.put("sku",           item.getProductSku());
                i.put("units",         item.getQuantity());
                i.put("selling_price", item.getUnitPricePaise() / 100.0);
                items.add(i);
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("order_id",            order.getId().substring(0, 8).toUpperCase());
        body.put("order_date",          order.getCreatedAt() != null
                ? order.getCreatedAt().toString() : Instant.now().toString());
        body.put("pickup_location",     cfg.shiprocketPickupLocation);
        body.put("channel_id",          "");
        body.put("comment",             order.getNotes() != null ? order.getNotes() : "");
        body.put("billing_customer_name",  shippingAddr.getName());
        body.put("billing_last_name",      "");
        body.put("billing_address",        shippingAddr.getLine1());
        body.put("billing_address_2",      shippingAddr.getLine2() != null ? shippingAddr.getLine2() : "");
        body.put("billing_city",           shippingAddr.getCity());
        body.put("billing_pincode",        shippingAddr.getPincode());
        body.put("billing_state",          shippingAddr.getState());
        body.put("billing_country",        "India");
        body.put("billing_email",          "");
        body.put("billing_phone",          shippingAddr.getPhone());
        body.put("shipping_is_billing",    true);
        body.put("order_items",            items);
        body.put("payment_method",         "Prepaid");
        body.put("sub_total",              order.getTotalPaise() / 100.0);
        body.put("length",                 15);
        body.put("breadth",                10);
        body.put("height",                 10);
        body.put("weight",                 0.5);

        try {
            String responseBody = post(BASE_URL + "/orders/create/adhoc",
                    JsonUtil.toJson(body), "Bearer " + token);
            JsonObject json = JsonUtil.parseObject(responseBody);
            String shipmentId = json.has("shipment_id")
                    ? json.get("shipment_id").getAsString() : null;
            log.info("Shiprocket shipment created: {} for orderId={}", shipmentId, order.getId());
            return shipmentId;
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw ApiException.internal("Shipping partner unreachable: " + e.getMessage());
        }
    }

    /**
     * Fetches tracking info for an AWB number.
     */
    public JsonObject trackShipment(String awb) {
        String token = getToken();
        try {
            String responseBody = get(BASE_URL + "/courier/track/awb/" + awb,
                    "Bearer " + token);
            return JsonUtil.parseObject(responseBody);
        } catch (IOException e) {
            throw ApiException.internal("Tracking request failed: " + e.getMessage());
        }
    }

    /**
     * Convenience overload that parses the JSON snapshot from Order.shippingAddress.
     */
    public String createShipmentFromOrder(Order order) {
        com.sasoori.model.Address addr = JsonUtil.GSON.fromJson(
                order.getShippingAddress(), com.sasoori.model.Address.class);
        return createShipment(order, addr);
    }

    // ── Auth token (cached 23 h) ──────────────────────────────────────────

    private String getToken() {
        long now = Instant.now().getEpochSecond();
        if (cachedToken.get() != null && tokenExpiresAt.get() > now) {
            return cachedToken.get();
        }
        return refreshToken();
    }

    private synchronized String refreshToken() {
        long now = Instant.now().getEpochSecond();
        if (cachedToken.get() != null && tokenExpiresAt.get() > now) {
            return cachedToken.get();
        }

        String body = JsonUtil.toJson(Map.of(
                "email",    cfg.shiprocketEmail,
                "password", cfg.shiprocketPassword));

        try {
            String responseBody = post(LOGIN_URL, body, null);
            JsonObject json = JsonUtil.parseObject(responseBody);
            String token = json.get("token").getAsString();
            cachedToken.set(token);
            tokenExpiresAt.set(now + 23 * 3600);
            log.info("Shiprocket token refreshed");
            return token;
        } catch (IOException e) {
            throw ApiException.internal("Shiprocket auth unreachable: " + e.getMessage());
        }
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
            throw new IOException("HTTP " + status);
        }
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String get(String urlStr, String authHeader) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        if (authHeader != null) conn.setRequestProperty("Authorization", authHeader);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("HTTP " + status);
        }
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
