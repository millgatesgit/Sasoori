package com.sasoori.servlet.admin;

import com.sasoori.dao.OrderDao;
import com.sasoori.dao.ShipmentDao;
import com.sasoori.dao.UserDao;
import com.sasoori.service.ShiprocketService;
import com.sasoori.exception.ApiException;
import com.sasoori.model.Product;
import com.sasoori.model.User;
import com.sasoori.service.ProductService;
import com.sasoori.servlet.BaseServlet;
import com.sasoori.util.JsonUtil;
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
 * Admin-only endpoints (requires role=ADMIN):
 * <pre>
 *   GET  /api/v1/admin/dashboard
 *   GET  /api/v1/admin/orders[?status=&page=&size=]
 *   PUT  /api/v1/admin/orders/{id}/status
 *   GET  /api/v1/admin/users[?page=&size=]
 *   PUT  /api/v1/admin/users/{id}/active
 *   PUT  /api/v1/admin/users/{id}/role
 *   POST /api/v1/admin/products
 *   PUT  /api/v1/admin/products/{id}
 *   DELETE /api/v1/admin/products/{id}
 * </pre>
 * Authentication is handled by JWTAuthFilter. Role check is performed here.
 */
@WebServlet(urlPatterns = "/api/v1/admin/*")
public class AdminServlet extends BaseServlet {

    private static final Logger log = LogManager.getLogger(AdminServlet.class);

    private OrderDao       orderDao;
    private UserDao        userDao;
    private ProductService productService;

    @Override
    public void init() {
        orderDao       = (OrderDao)       getServletContext().getAttribute("orderDao");
        userDao        = (UserDao)        getServletContext().getAttribute("userDao");
        productService = (ProductService) getServletContext().getAttribute("productService");
    }

    // ── GET ───────────────────────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            requireAdmin(req);
            String path = subPath(req);

            String[] parts = path.split("/");
            if ("/dashboard".equals(path)) {
                handleDashboard(resp);
            } else if (path.startsWith("/orders")) {
                handleListOrders(req, resp, path);
            } else if (path.startsWith("/users")) {
                handleListUsers(req, resp);
            } else if (path.matches("/shipments")) {
                handleListShipments(req, resp);
            } else if (path.matches("/shipments/[^/]+/track")) {
                String orderId = parts[parts.length - 2];
                handleTrackShipment(req, resp, orderId);
            } else if (path.matches("/invoices")) {
                handleListInvoices(req, resp);
            } else if (path.matches("/invoices/[^/]+")) {
                handleGetInvoice(req, resp, parts[parts.length - 1]);
            } else {
                throw ApiException.notFound("Unknown admin endpoint: " + path);
            }
        });
    }

    // ── PUT ───────────────────────────────────────────────────────────────

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            requireAdmin(req);
            String path = subPath(req);

            if (path.matches("/orders/[^/]+/status")) {
                String orderId = extractSegment(path, 2);
                handleUpdateOrderStatus(req, resp, orderId);
            } else if (path.matches("/users/[^/]+/active")) {
                String userId = extractSegment(path, 2);
                handleSetUserActive(req, resp, userId);
            } else if (path.matches("/users/[^/]+/role")) {
                String userId = extractSegment(path, 2);
                handleSetUserRole(req, resp, userId);
            } else if (path.matches("/products/[^/]+")) {
                String productId = path.substring("/products/".length());
                handleUpdateProduct(req, resp, productId);
            } else {
                throw ApiException.notFound("Unknown admin endpoint: " + path);
            }
        });
    }

    // ── POST ──────────────────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            requireAdmin(req);
            String path = subPath(req);

            String[] parts = path.split("/");
            if ("/products".equals(path) || "/products/".equals(path)) {
                handleCreateProduct(req, resp);
            } else if (path.matches("/shipments/[^/]+")) {
                String orderId = parts[parts.length - 1];
                handleCreateShipment(req, resp, orderId);
            } else {
                throw ApiException.notFound("Unknown admin endpoint: " + path);
            }
        });
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp, () -> {
            requireAdmin(req);
            String path = subPath(req);

            if (path.matches("/products/[^/]+")) {
                String productId = path.substring("/products/".length());
                productService.delete(productId);
                sendSuccess(resp, Map.of("message", "Product deleted"));
            } else {
                throw ApiException.notFound("Unknown admin endpoint: " + path);
            }
        });
    }

    // ── Handlers ──────────────────────────────────────────────────────────

    private void handleDashboard(HttpServletResponse resp) throws Exception {
        OrderDao.DashboardStats stats = orderDao.getStats();

        // Top 5 products by revenue (from order_items)
        List<Map<String, Object>> topProducts = getTopProducts();

        // Low stock products (stock_qty < 10)
        List<Map<String, Object>> lowStock = getLowStockProducts();

        sendSuccess(resp, Map.of(
                "totalRevenuePaise", stats.totalRevenuePaise(),
                "totalOrders",       stats.totalOrders(),
                "pendingOrders",     stats.pendingOrders(),
                "shippedOrders",     stats.shippedOrders(),
                "topProducts",       topProducts,
                "lowStockProducts",  lowStock
        ));
    }

    private void handleListOrders(HttpServletRequest req, HttpServletResponse resp,
                                   String path) throws Exception {
        // GET /admin/orders
        if (!path.equals("/orders") && !path.equals("/orders/")) {
            throw ApiException.notFound("Unknown admin orders path: " + path);
        }
        String status = req.getParameter("status");
        int page = parseIntParam(req, "page", 1);
        int size = parseIntParam(req, "size", 20);

        OrderDao.OrderPage p = orderDao.findAll(status, page, size);
        int totalPages = (int) Math.ceil((double) p.total() / size);

        sendSuccess(resp, Map.of(
                "orders",     p.orders().stream().map(this::orderToMap).collect(Collectors.toList()),
                "total",      p.total(),
                "page",       page,
                "totalPages", totalPages
        ));
    }

    private void handleUpdateOrderStatus(HttpServletRequest req, HttpServletResponse resp,
                                          String orderId) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = JsonUtil.fromJson(readBody(req), Map.class);
        if (body == null || !body.containsKey("status"))
            throw ApiException.badRequest("MISSING_STATUS", "status field is required");

        String newStatus = body.get("status").toString();
        orderDao.updateStatus(orderId, newStatus);
        sendSuccess(resp, Map.of("message", "Status updated to " + newStatus));
    }

    private void handleListUsers(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        int page = parseIntParam(req, "page", 1);
        int size = parseIntParam(req, "size", 20);

        UserDao.UserPage p = userDao.findAll(page, size);
        int totalPages = (int) Math.ceil((double) p.total() / size);

        sendSuccess(resp, Map.of(
                "users",      p.users().stream().map(this::userToMap).collect(Collectors.toList()),
                "total",      p.total(),
                "page",       page,
                "totalPages", totalPages
        ));
    }

    private void handleSetUserActive(HttpServletRequest req, HttpServletResponse resp,
                                      String userId) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = JsonUtil.fromJson(readBody(req), Map.class);
        if (body == null || !body.containsKey("active"))
            throw ApiException.badRequest("MISSING_FIELD", "active field is required");

        boolean active = Boolean.parseBoolean(body.get("active").toString());
        userDao.setActive(userId, active);
        sendSuccess(resp, Map.of("message", "User " + (active ? "activated" : "deactivated")));
    }

    private void handleSetUserRole(HttpServletRequest req, HttpServletResponse resp,
                                    String userId) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = JsonUtil.fromJson(readBody(req), Map.class);
        if (body == null || !body.containsKey("role"))
            throw ApiException.badRequest("MISSING_FIELD", "role field is required");

        String role = body.get("role").toString().toUpperCase();
        if (!"CUSTOMER".equals(role) && !"ADMIN".equals(role))
            throw ApiException.badRequest("INVALID_ROLE", "Role must be CUSTOMER or ADMIN");

        userDao.setRole(userId, role);
        sendSuccess(resp, Map.of("message", "Role updated to " + role));
    }

    private void handleCreateProduct(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Product p = parseBody(req, Product.class);
        Product created = productService.create(p);
        sendSuccess(resp, 201, productToMap(created));
    }

    private void handleUpdateProduct(HttpServletRequest req, HttpServletResponse resp,
                                      String productId) throws Exception {
        Product p = parseBody(req, Product.class);
        p.setId(productId);
        Product updated = productService.update(p);
        sendSuccess(resp, productToMap(updated));
    }

    // ── Aggregate helpers ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getTopProducts() {
        String sql = """
            SELECT oi.product_name, SUM(oi.total_paise) AS revenue, SUM(oi.quantity) AS units
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.status NOT IN ('CANCELLED','REFUNDED')
            GROUP BY oi.product_name
            ORDER BY revenue DESC
            LIMIT 5
            """;
        try (var c = orderDao.getDataSource().getConnection();
             var ps = c.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            List<Map<String, Object>> list = new java.util.ArrayList<>();
            while (rs.next()) {
                list.add(Map.of(
                        "name",    rs.getString("product_name"),
                        "revenue", rs.getLong("revenue"),
                        "units",   rs.getInt("units")
                ));
            }
            return list;
        } catch (Exception e) {
            log.warn("getTopProducts failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> getLowStockProducts() {
        String sql = """
            SELECT p.id, p.name, p.sku, p.stock_qty
            FROM products p
            WHERE p.is_active = TRUE AND p.stock_qty < 10
            ORDER BY p.stock_qty ASC
            LIMIT 20
            """;
        try (var c = orderDao.getDataSource().getConnection();
             var ps = c.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            List<Map<String, Object>> list = new java.util.ArrayList<>();
            while (rs.next()) {
                list.add(Map.of(
                        "id",       rs.getString("id"),
                        "name",     rs.getString("name"),
                        "sku",      rs.getString("sku"),
                        "stockQty", rs.getInt("stock_qty")
                ));
            }
            return list;
        } catch (Exception e) {
            log.warn("getLowStockProducts failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Mappers ───────────────────────────────────────────────────────────

    private Map<String, Object> orderToMap(com.sasoori.model.Order o) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",         o.getId());
        m.put("userId",     o.getUserId());
        m.put("status",     o.getStatus());
        m.put("totalPaise", o.getTotalPaise());
        m.put("createdAt",  o.getCreatedAt() != null
                ? o.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        return m;
    }

    private Map<String, Object> userToMap(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",        u.getId());
        m.put("name",      u.getName());
        m.put("email",     u.getEmail());
        m.put("phone",     u.getPhone());
        m.put("role",      u.getRole());
        m.put("isActive",  u.isActive());
        m.put("createdAt", u.getCreatedAt() != null
                ? u.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        return m;
    }

    private Map<String, Object> productToMap(Product p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",           p.getId());
        m.put("name",         p.getName());
        m.put("slug",         p.getSlug());
        m.put("sku",          p.getSku());
        m.put("categoryId",   p.getCategoryId());
        m.put("categoryName", p.getCategoryName());
        m.put("pricePaise",   p.getPricePaise());
        m.put("mrpPaise",     p.getMrpPaise());
        m.put("stockQty",     p.getStockQty());
        m.put("weightGrams",  p.getWeightGrams());
        m.put("isActive",     p.isActive());
        m.put("isFeatured",   p.isFeatured());
        m.put("images",       p.getImages());
        m.put("tags",         p.getTags());
        m.put("description",  p.getDescription());
        return m;
    }

    // ── Shipments ────────────────────────────────────────────────────────

    private void handleListShipments(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ShipmentDao shipmentDao = (ShipmentDao) req.getServletContext().getAttribute("shipmentDao");
        String status = req.getParameter("status");
        int page = parseIntParam(req, "page", 1);
        int size = parseIntParam(req, "size", 20);
        ShipmentDao.ShipmentPage result = shipmentDao.findAll(status, page, size);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("shipments", result.shipments());
        data.put("total",     result.total());
        data.put("page",      page);
        data.put("totalPages", (int) Math.ceil((double) result.total() / size));
        sendSuccess(resp, data);
    }

    private void handleCreateShipment(HttpServletRequest req, HttpServletResponse resp, String orderId) throws IOException {
        OrderDao orderDao = (OrderDao) req.getServletContext().getAttribute("orderDao");
        ShiprocketService shiprocket = (ShiprocketService) req.getServletContext().getAttribute("shiprocketService");
        try {
            // Load order and create shipment via Shiprocket
            var order = orderDao.findById(java.util.UUID.fromString(orderId));
            if (order == null) { sendError(resp, 404, "ORDER_NOT_FOUND", "Order not found"); return; }
            shiprocket.createShipmentFromOrder(order);
            sendSuccess(resp, Map.of("message", "Shipment created successfully"));
        } catch (Exception e) {
            log.error("Create shipment failed for order {}: {}", orderId, e.getMessage());
            sendError(resp, 500, "SHIPMENT_ERROR", e.getMessage());
        }
    }

    private void handleTrackShipment(HttpServletRequest req, HttpServletResponse resp, String orderId) throws IOException {
        ShipmentDao shipmentDao = (ShipmentDao) req.getServletContext().getAttribute("shipmentDao");
        ShiprocketService shiprocket = (ShiprocketService) req.getServletContext().getAttribute("shiprocketService");
        ShipmentDao.ShipmentRow shipment = shipmentDao.findByOrderId(orderId);
        if (shipment == null || shipment.awbCode() == null) {
            sendError(resp, 404, "SHIPMENT_NOT_FOUND", "No shipment found for this order");
            return;
        }
        try {
            Object tracking = shiprocket.trackShipment(shipment.awbCode());
            sendSuccess(resp, Map.of("tracking", tracking, "shipment", shipment));
        } catch (Exception e) {
            log.error("Track shipment failed for AWB {}: {}", shipment.awbCode(), e.getMessage());
            sendError(resp, 500, "TRACK_ERROR", e.getMessage());
        }
    }

    // ── Invoices ─────────────────────────────────────────────────────────

    private void handleListInvoices(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        OrderDao orderDao = (OrderDao) req.getServletContext().getAttribute("orderDao");
        int page = parseIntParam(req, "page", 1);
        int size = parseIntParam(req, "size", 20);
        // Invoices are derived from PAID orders (findAll only supports single status)
        OrderDao.OrderPage result;
        try {
            result = orderDao.findAll("PAID", page, size);
        } catch (Exception e) {
            log.error("handleListInvoices failed: {}", e.getMessage());
            sendError(resp, 500, "INVOICE_ERROR", e.getMessage());
            return;
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("invoices",   result.orders());
        data.put("total",      result.total());
        data.put("page",       page);
        data.put("totalPages", (int) Math.ceil((double) result.total() / size));
        sendSuccess(resp, data);
    }

    private void handleGetInvoice(HttpServletRequest req, HttpServletResponse resp, String orderId) throws IOException {
        OrderDao orderDao = (OrderDao) req.getServletContext().getAttribute("orderDao");
        try {
            var order = orderDao.findById(java.util.UUID.fromString(orderId));
            if (order == null) { sendError(resp, 404, "ORDER_NOT_FOUND", "Order not found"); return; }
            sendSuccess(resp, Map.of("order", order));
        } catch (Exception e) {
            sendError(resp, 400, "INVALID_ID", "Invalid order ID");
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    private void requireAdmin(HttpServletRequest req) {
        if (!"ADMIN".equals(getUserRole(req)))
            throw ApiException.forbidden("Admin access required");
    }

    /** Returns the Nth path segment (1-based, after /api/v1/admin). */
    private String extractSegment(String path, int n) {
        String[] parts = path.split("/");
        // path="/orders/abc-uuid/status" → parts=["","orders","abc-uuid","status"]
        return parts.length > n ? parts[n] : "";
    }

    private static String subPath(HttpServletRequest req) {
        String uri   = req.getRequestURI();
        String ctx   = req.getContextPath();
        String after = uri.substring(ctx.length());
        int    idx   = after.indexOf("/api/v1/admin");
        return idx >= 0 ? after.substring(idx + "/api/v1/admin".length()) : after;
    }

    private static int parseIntParam(HttpServletRequest req, String name, int def) {
        String v = req.getParameter(name);
        if (v == null || v.isBlank()) return def;
        try { return Math.max(1, Integer.parseInt(v)); }
        catch (NumberFormatException e) { return def; }
    }
}
