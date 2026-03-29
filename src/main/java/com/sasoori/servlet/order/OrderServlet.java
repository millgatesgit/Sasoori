package com.sasoori.servlet.order;

import com.sasoori.dao.OrderDao;
import com.sasoori.dto.PlaceOrderRequest;
import com.sasoori.exception.ApiException;
import com.sasoori.model.Order;
import com.sasoori.model.OrderItem;
import com.sasoori.service.OrderService;
import com.sasoori.servlet.BaseServlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Order endpoints:
 * <pre>
 *   POST   /api/v1/orders            — place order
 *   GET    /api/v1/orders            — list user's orders (paginated)
 *   GET    /api/v1/orders/{id}       — get single order with items
 *   POST   /api/v1/orders/{id}/cancel — cancel order
 * </pre>
 * All routes require authentication.
 */
@WebServlet(urlPatterns = "/api/v1/orders/*")
public class OrderServlet extends BaseServlet {

    private static final Logger log = LogManager.getLogger(OrderServlet.class);

    private OrderService orderService;
    private OrderDao     orderDao;

    @Override
    public void init() {
        orderService = (OrderService) getServletContext().getAttribute("orderService");
        orderDao     = (OrderDao)     getServletContext().getAttribute("orderDao");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = subPath(req);
        handle(req, resp, () -> {
            String userId = requireUserId(req);
            if (path.isEmpty() || "/".equals(path)) {
                handlePlaceOrder(req, resp, userId);
            } else if (path.matches("/[^/]+/cancel")) {
                String orderId = path.substring(1, path.lastIndexOf("/cancel"));
                orderService.cancelOrder(userId, orderId);
                sendSuccess(resp, Map.of("message", "Order cancelled successfully"));
            } else {
                throw ApiException.notFound("Unknown order endpoint: " + path);
            }
        });
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = subPath(req);
        handle(req, resp, () -> {
            String userId = requireUserId(req);
            if (path.isEmpty() || "/".equals(path)) {
                handleListOrders(req, resp, userId);
            } else {
                String orderId = path.startsWith("/") ? path.substring(1) : path;
                handleGetOrder(resp, userId, orderId);
            }
        });
    }

    // ── Handlers ──────────────────────────────────────────────────────────

    private void handlePlaceOrder(HttpServletRequest req, HttpServletResponse resp,
                                   String userId) throws Exception {
        PlaceOrderRequest body = parseBody(req, PlaceOrderRequest.class);
        if (body.addressId <= 0)
            throw ApiException.badRequest("MISSING_ADDRESS", "A delivery address is required");

        OrderService.PlaceOrderResult result =
                orderService.placeOrder(userId, body.addressId, body.notes);

        sendSuccess(resp, 201, Map.of(
                "orderId",         result.orderId(),
                "razorpayOrderId", result.razorpayOrderId(),
                "totalPaise",      result.totalPaise(),
                "currency",        result.currency()
        ));
    }

    private void handleListOrders(HttpServletRequest req, HttpServletResponse resp,
                                   String userId) throws Exception {
        int page = parseIntParam(req, "page", 1);
        int size = parseIntParam(req, "size", 10);

        OrderDao.OrderPage p = orderDao.findByUserId(userId, page, size);
        int totalPages = (int) Math.ceil((double) p.total() / size);

        sendSuccess(resp, Map.of(
                "orders",     p.orders().stream().map(this::toResponse).collect(Collectors.toList()),
                "total",      p.total(),
                "page",       page,
                "size",       size,
                "totalPages", totalPages
        ));
    }

    private void handleGetOrder(HttpServletResponse resp,
                                 String userId, String orderId) throws Exception {
        Order order = orderDao.findById(orderId, userId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        sendSuccess(resp, toResponse(order));
    }

    // ── Mapper ────────────────────────────────────────────────────────────

    private Map<String, Object> toResponse(Order o) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",              o.getId());
        m.put("status",          o.getStatus());
        m.put("subtotalPaise",   o.getSubtotalPaise());
        m.put("shippingPaise",   o.getShippingPaise());
        m.put("discountPaise",   o.getDiscountPaise());
        m.put("totalPaise",      o.getTotalPaise());
        m.put("razorpayOrderId", o.getRazorpayOrderId());
        m.put("notes",           o.getNotes());
        m.put("createdAt",       o.getCreatedAt() != null
                ? o.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);

        // shippingAddress is a JSON string — parse it back to object for clean response
        try {
            m.put("shippingAddress", com.sasoori.util.JsonUtil.parseObject(o.getShippingAddress()));
        } catch (Exception e) {
            m.put("shippingAddress", o.getShippingAddress());
        }

        List<OrderItem> items = o.getItems();
        if (items != null) {
            m.put("items", items.stream().map(i -> Map.of(
                    "productId",      String.valueOf(i.getProductId()),
                    "productName",    i.getProductName(),
                    "productSku",     i.getProductSku(),
                    "quantity",       i.getQuantity(),
                    "unitPricePaise", i.getUnitPricePaise(),
                    "totalPaise",     i.getTotalPaise()
            )).collect(Collectors.toList()));
        }
        return m;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String subPath(HttpServletRequest req) {
        String uri   = req.getRequestURI();
        String ctx   = req.getContextPath();
        String after = uri.substring(ctx.length());
        int    idx   = after.indexOf("/api/v1/orders");
        return idx >= 0 ? after.substring(idx + "/api/v1/orders".length()) : after;
    }

    private static int parseIntParam(HttpServletRequest req, String name, int defaultVal) {
        String v = req.getParameter(name);
        if (v == null || v.isBlank()) return defaultVal;
        try { return Math.max(1, Integer.parseInt(v)); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}
