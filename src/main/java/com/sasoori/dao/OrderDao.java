package com.sasoori.dao;

import com.sasoori.exception.ApiException;
import com.sasoori.model.Order;
import com.sasoori.model.OrderItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access for {@code orders} and {@code order_items} tables.
 */
public class OrderDao {

    private static final Logger log = LogManager.getLogger(OrderDao.class);

    private final DataSource ds;

    public OrderDao(DataSource ds) {
        this.ds = ds;
    }

    public DataSource getDataSource() { return ds; }

    // ── Result container ──────────────────────────────────────────────────

    public record OrderPage(List<Order> orders, int total) {}

    // ── Create (SERIALIZABLE transaction) ────────────────────────────────

    /**
     * Creates an order with stock check + decrement inside a SERIALIZABLE
     * transaction.  Retries once on serialization failure (SQLState 40001).
     * implements: stock-safety, AC-orders-1
     */
    public Order create(Order order, List<OrderItem> items) throws SQLException {
        try {
            return doCreate(order, items);
        } catch (SQLException e) {
            if ("40001".equals(e.getSQLState())) {
                log.warn("Serialization failure on order create — retrying once");
                return doCreate(order, items);  // one retry
            }
            throw e;
        }
    }

    private Order doCreate(Order order, List<OrderItem> items) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try {
                // 1. Stock check + decrement for each item
                for (OrderItem item : items) {
                    String stockSql = "SELECT stock_qty FROM products WHERE id = ?::uuid FOR UPDATE";
                    try (PreparedStatement ps = c.prepareStatement(stockSql)) {
                        ps.setString(1, item.getProductId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw ApiException.badRequest("PRODUCT_NOT_FOUND",
                                        "Product not found: " + item.getProductId());
                            }
                            int stock = rs.getInt(1);
                            if (stock < item.getQuantity()) {
                                throw new ApiException(409, "INSUFFICIENT_STOCK",
                                        "Insufficient stock for: " + item.getProductName());
                            }
                        }
                    }
                    String deductSql = "UPDATE products SET stock_qty = stock_qty - ? WHERE id = ?::uuid";
                    try (PreparedStatement ps = c.prepareStatement(deductSql)) {
                        ps.setInt(1, item.getQuantity());
                        ps.setString(2, item.getProductId());
                        ps.executeUpdate();
                    }
                }

                // 2. Insert order
                String insertOrderSql = """
                    INSERT INTO orders
                        (user_id, status, subtotal_paise, shipping_paise, discount_paise,
                         total_paise, shipping_address, razorpay_order_id, notes)
                    VALUES (?::uuid, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                    RETURNING id, created_at, updated_at
                    """;
                String orderId;
                try (PreparedStatement ps = c.prepareStatement(insertOrderSql)) {
                    ps.setString(1, order.getUserId());
                    ps.setString(2, order.getStatus() != null ? order.getStatus() : "PENDING");
                    ps.setInt(3, order.getSubtotalPaise());
                    ps.setInt(4, order.getShippingPaise());
                    ps.setInt(5, order.getDiscountPaise());
                    ps.setInt(6, order.getTotalPaise());
                    ps.setString(7, order.getShippingAddress());
                    ps.setString(8, order.getRazorpayOrderId());
                    ps.setString(9, order.getNotes());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        orderId = rs.getString("id");
                        order.setId(orderId);
                        Timestamp ca = rs.getTimestamp("created_at");
                        if (ca != null) order.setCreatedAt(ca.toInstant().atOffset(ZoneOffset.UTC));
                    }
                }

                // 3. Insert order items (batch)
                String insertItemSql = """
                    INSERT INTO order_items
                        (order_id, product_id, product_name, product_sku,
                         quantity, unit_price_paise, total_paise)
                    VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?)
                    """;
                try (PreparedStatement ps = c.prepareStatement(insertItemSql)) {
                    for (OrderItem item : items) {
                        ps.setString(1, orderId);
                        ps.setString(2, item.getProductId());
                        ps.setString(3, item.getProductName());
                        ps.setString(4, item.getProductSku());
                        ps.setInt(5, item.getQuantity());
                        ps.setInt(6, item.getUnitPricePaise());
                        ps.setInt(7, item.getTotalPaise());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // 4. Insert payment record
                String insertPaymentSql = """
                    INSERT INTO payments
                        (order_id, razorpay_order_id, amount_paise, currency, status)
                    VALUES (?::uuid, ?, ?, 'INR', 'CREATED')
                    """;
                try (PreparedStatement ps = c.prepareStatement(insertPaymentSql)) {
                    ps.setString(1, orderId);
                    ps.setString(2, order.getRazorpayOrderId());
                    ps.setInt(3, order.getTotalPaise());
                    ps.executeUpdate();
                }

                c.commit();
                order.setItems(items);
                return order;

            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public Optional<Order> findById(String id, String userId) throws SQLException {
        String sql = """
            SELECT id, user_id, status, subtotal_paise, shipping_paise, discount_paise,
                   total_paise, shipping_address, razorpay_order_id, notes, created_at, updated_at
            FROM orders
            WHERE id = ?::uuid AND user_id = ?::uuid
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Order order = mapOrder(rs);
                order.setItems(fetchItems(c, id));
                return Optional.of(order);
            }
        }
    }

    /** Admin lookup: finds an order by its UUID without user scope. */
    public Order findById(UUID orderId) {
        String sql = """
            SELECT id, user_id, status, subtotal_paise, shipping_paise, discount_paise,
                   total_paise, shipping_address, razorpay_order_id, notes, created_at, updated_at
            FROM orders
            WHERE id = ?
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Order order = mapOrder(rs);
                order.setItems(fetchItems(c, order.getId()));
                return order;
            }
        } catch (SQLException e) {
            log.error("OrderDao.findById: {}", e.getMessage(), e);
        }
        return null;
    }

    public OrderPage findByUserId(String userId, int page, int size) throws SQLException {
        size = Math.min(size, 20);
        int offset = (Math.max(page, 1) - 1) * size;

        String countSql = "SELECT COUNT(*) FROM orders WHERE user_id = ?::uuid";
        String dataSql  = """
            SELECT id, user_id, status, subtotal_paise, shipping_paise, discount_paise,
                   total_paise, shipping_address, razorpay_order_id, notes, created_at, updated_at
            FROM orders
            WHERE user_id = ?::uuid
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;
        try (Connection c = ds.getConnection()) {
            int total;
            try (PreparedStatement ps = c.prepareStatement(countSql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next(); total = rs.getInt(1);
                }
            }
            List<Order> orders = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(dataSql)) {
                ps.setString(1, userId);
                ps.setInt(2, size);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) orders.add(mapOrder(rs));
                }
            }
            return new OrderPage(orders, total);
        }
    }

    // ── Admin queries ─────────────────────────────────────────────────────

    /** Lists all orders (admin), optionally filtered by status. */
    public OrderPage findAll(String status, int page, int size) throws SQLException {
        size = Math.min(size, 50);
        int offset = (Math.max(page, 1) - 1) * size;
        boolean hasStatus = status != null && !status.isBlank();
        String where   = hasStatus ? "WHERE status = ?" : "";
        String countSql = "SELECT COUNT(*) FROM orders " + where;
        String dataSql  = "SELECT id, user_id, status, subtotal_paise, shipping_paise, discount_paise, " +
                          "total_paise, shipping_address, razorpay_order_id, notes, created_at, updated_at " +
                          "FROM orders " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection c = ds.getConnection()) {
            int total;
            try (PreparedStatement ps = c.prepareStatement(countSql)) {
                if (hasStatus) ps.setString(1, status);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); total = rs.getInt(1); }
            }
            List<Order> orders = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(dataSql)) {
                int i = 1;
                if (hasStatus) ps.setString(i++, status);
                ps.setInt(i++, size); ps.setInt(i, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) orders.add(mapOrder(rs));
                }
            }
            return new OrderPage(orders, total);
        }
    }

    public record DashboardStats(long totalRevenuePaise, int totalOrders,
                                  int pendingOrders, int shippedOrders) {}

    public DashboardStats getStats() throws SQLException {
        String sql = """
            SELECT
                COALESCE(SUM(CASE WHEN status NOT IN ('CANCELLED','REFUNDED') THEN total_paise ELSE 0 END), 0) AS revenue,
                COUNT(*)                                                               AS total,
                COUNT(*) FILTER (WHERE status IN ('PENDING','PAID','PROCESSING'))      AS pending,
                COUNT(*) FILTER (WHERE status = 'SHIPPED')                            AS shipped
            FROM orders
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new DashboardStats(
                        rs.getLong("revenue"),
                        rs.getInt("total"),
                        rs.getInt("pending"),
                        rs.getInt("shipped"));
            }
        }
        return new DashboardStats(0, 0, 0, 0);
    }

    public void updateStatus(String orderId, String newStatus) throws SQLException {
        String sql = "UPDATE orders SET status = ?, updated_at = NOW() WHERE id = ?::uuid";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setString(2, orderId);
            ps.executeUpdate();
        }
    }

    /**
     * Cancels an order: updates status to CANCELLED and restores product stock
     * in a single transaction.
     */
    public void cancelWithStockRestore(String orderId) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Restore stock for each item
                String itemsSql = """
                    SELECT product_id, quantity FROM order_items
                    WHERE order_id = ?::uuid AND product_id IS NOT NULL
                    """;
                try (PreparedStatement ps = c.prepareStatement(itemsSql)) {
                    ps.setString(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        String restoreSql = "UPDATE products SET stock_qty = stock_qty + ? WHERE id = ?::uuid";
                        try (PreparedStatement rps = c.prepareStatement(restoreSql)) {
                            while (rs.next()) {
                                rps.setInt(1, rs.getInt("quantity"));
                                rps.setString(2, rs.getString("product_id"));
                                rps.addBatch();
                            }
                            rps.executeBatch();
                        }
                    }
                }
                // 2. Update order status
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE orders SET status='CANCELLED', updated_at=NOW() WHERE id=?::uuid")) {
                    ps.setString(1, orderId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private List<OrderItem> fetchItems(Connection c, String orderId) throws SQLException {
        String sql = """
            SELECT id, order_id, product_id, product_name, product_sku,
                   quantity, unit_price_paise, total_paise
            FROM order_items WHERE order_id = ?::uuid
            """;
        List<OrderItem> items = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) items.add(mapItem(rs));
            }
        }
        return items;
    }

    private static Order mapOrder(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setId(rs.getString("id"));
        o.setUserId(rs.getString("user_id"));
        o.setStatus(rs.getString("status"));
        o.setSubtotalPaise(rs.getInt("subtotal_paise"));
        o.setShippingPaise(rs.getInt("shipping_paise"));
        o.setDiscountPaise(rs.getInt("discount_paise"));
        o.setTotalPaise(rs.getInt("total_paise"));
        o.setShippingAddress(rs.getString("shipping_address"));
        o.setRazorpayOrderId(rs.getString("razorpay_order_id"));
        o.setNotes(rs.getString("notes"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) o.setCreatedAt(ca.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) o.setUpdatedAt(ua.toInstant().atOffset(ZoneOffset.UTC));
        return o;
    }

    private static OrderItem mapItem(ResultSet rs) throws SQLException {
        OrderItem i = new OrderItem();
        i.setId(rs.getInt("id"));
        i.setOrderId(rs.getString("order_id"));
        i.setProductId(rs.getString("product_id"));
        i.setProductName(rs.getString("product_name"));
        i.setProductSku(rs.getString("product_sku"));
        i.setQuantity(rs.getInt("quantity"));
        i.setUnitPricePaise(rs.getInt("unit_price_paise"));
        i.setTotalPaise(rs.getInt("total_paise"));
        return i;
    }
}
