package com.sasoori.servlet;

import com.sasoori.config.AppConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * GET /api/v1/config — returns frontend-safe configuration (no secrets).
 * Public endpoint — no authentication required.
 */
@WebServlet(urlPatterns = "/api/v1/config")
public class ConfigServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            AppConfig cfg = AppConfig.get();
            sendSuccess(resp, Map.of(
                    "razorpayKeyId",              cfg.razorpayKeyId,
                    "freeShippingThresholdPaise", cfg.freeShippingThresholdPaise,
                    "shippingChargePaise",        cfg.shippingChargePaise
            ));
        });
    }
}
