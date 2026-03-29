package com.sasoori.dao;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.sql.*;

/**
 * Data-access for the {@code payments} table.
 */
public class PaymentDao {

    private static final Logger log = LogManager.getLogger(PaymentDao.class);

    private final DataSource ds;

    public PaymentDao(DataSource ds) {
        this.ds = ds;
    }

    /** Mark payment as captured after client-side signature verification. */
    public void updateCaptured(String orderId,
                                String razorpayPaymentId,
                                String razorpaySignature) throws SQLException {
        String sql = """
            UPDATE payments
               SET razorpay_payment_id = ?,
                   razorpay_signature  = ?,
                   status              = 'CAPTURED',
                   updated_at          = NOW()
             WHERE order_id = ?::uuid
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, razorpayPaymentId);
            ps.setString(2, razorpaySignature);
            ps.setString(3, orderId);
            ps.executeUpdate();
        }
    }

    /** Mark payment as failed. */
    public void updateFailed(String orderId) throws SQLException {
        String sql = """
            UPDATE payments
               SET status     = 'FAILED',
                   updated_at = NOW()
             WHERE order_id = ?::uuid
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, orderId);
            ps.executeUpdate();
        }
    }

    /** Store raw webhook JSON and update status (called from webhook handler). */
    public void saveWebhookPayload(String orderId,
                                    String payload,
                                    String newStatus) throws SQLException {
        String sql = """
            UPDATE payments
               SET webhook_payload = ?::jsonb,
                   status          = ?,
                   updated_at      = NOW()
             WHERE order_id = ?::uuid
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, payload);
            ps.setString(2, newStatus);
            ps.setString(3, orderId);
            ps.executeUpdate();
        }
    }

    /** Update the payment method (e.g. "upi", "card") from webhook data. */
    public void updateMethod(String orderId, String method) throws SQLException {
        String sql = """
            UPDATE payments
               SET method     = ?,
                   updated_at = NOW()
             WHERE order_id = ?::uuid
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, method);
            ps.setString(2, orderId);
            ps.executeUpdate();
        }
    }

    /** Look up the Razorpay order_id stored against our order_id. */
    public String getRazorpayOrderId(String orderId) throws SQLException {
        String sql = "SELECT razorpay_order_id FROM payments WHERE order_id = ?::uuid";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    /** Reverse lookup: find our internal order_id from a Razorpay order_id. */
    public String findOrderIdByRazorpayOrderId(String razorpayOrderId) throws SQLException {
        String sql = "SELECT order_id FROM payments WHERE razorpay_order_id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, razorpayOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }
}
