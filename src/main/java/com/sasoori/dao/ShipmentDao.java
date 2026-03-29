package com.sasoori.dao;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public class ShipmentDao {

    private static final Logger log = LogManager.getLogger(ShipmentDao.class);
    private final DataSource ds;

    public ShipmentDao(DataSource ds) { this.ds = ds; }

    /** Returns a page of shipments joined with order info */
    public record ShipmentRow(
        String orderId, String shiprocketOrderId, String shiprocketShipmentId,
        String awbCode, String courierName, String trackingUrl,
        String status, String estimatedDelivery, String createdAt,
        // from orders join
        String orderStatus, int totalPaise
    ) {}

    public record ShipmentPage(List<ShipmentRow> shipments, int total) {}

    public ShipmentPage findAll(String statusFilter, int page, int size) {
        List<ShipmentRow> rows = new ArrayList<>();
        int total = 0;
        String where = (statusFilter != null && !statusFilter.isBlank()) ? " WHERE s.status = ?" : "";
        String countSql = "SELECT COUNT(*) FROM shipments s" + where;
        String dataSql  = """
            SELECT s.order_id, s.shiprocket_order_id, s.shiprocket_shipment_id,
                   s.awb_code, s.courier_name, s.tracking_url, s.status,
                   s.estimated_delivery::text, s.created_at::text,
                   o.status AS order_status, o.total_paise
            FROM shipments s
            JOIN orders o ON o.id = s.order_id
            """ + where + " ORDER BY s.created_at DESC LIMIT ? OFFSET ?";

        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(countSql)) {
                if (!where.isBlank()) ps.setString(1, statusFilter);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) total = rs.getInt(1);
            }
            try (PreparedStatement ps = c.prepareStatement(dataSql)) {
                int idx = 1;
                if (!where.isBlank()) ps.setString(idx++, statusFilter);
                ps.setInt(idx++, size);
                ps.setInt(idx, (page - 1) * size);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    rows.add(new ShipmentRow(
                        rs.getString("order_id"),
                        rs.getString("shiprocket_order_id"),
                        rs.getString("shiprocket_shipment_id"),
                        rs.getString("awb_code"),
                        rs.getString("courier_name"),
                        rs.getString("tracking_url"),
                        rs.getString("status"),
                        rs.getString("estimated_delivery"),
                        rs.getString("created_at"),
                        rs.getString("order_status"),
                        rs.getInt("total_paise")
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("ShipmentDao.findAll failed: {}", e.getMessage(), e);
        }
        return new ShipmentPage(rows, total);
    }

    public ShipmentRow findByOrderId(String orderId) {
        String sql = """
            SELECT s.order_id, s.shiprocket_order_id, s.shiprocket_shipment_id,
                   s.awb_code, s.courier_name, s.tracking_url, s.status,
                   s.estimated_delivery::text, s.created_at::text,
                   o.status AS order_status, o.total_paise
            FROM shipments s
            JOIN orders o ON o.id = s.order_id
            WHERE s.order_id = ?
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, java.util.UUID.fromString(orderId));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new ShipmentRow(
                    rs.getString("order_id"), rs.getString("shiprocket_order_id"),
                    rs.getString("shiprocket_shipment_id"), rs.getString("awb_code"),
                    rs.getString("courier_name"), rs.getString("tracking_url"),
                    rs.getString("status"), rs.getString("estimated_delivery"),
                    rs.getString("created_at"), rs.getString("order_status"),
                    rs.getInt("total_paise")
                );
            }
        } catch (SQLException e) {
            log.error("ShipmentDao.findByOrderId failed: {}", e.getMessage(), e);
        }
        return null;
    }
}
