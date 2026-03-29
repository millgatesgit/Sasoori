package com.sasoori.dao;

import com.sasoori.model.CartItem;
import com.sasoori.util.JsonUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Data-access for {@code carts} and {@code cart_items} tables.
 * All queries use PreparedStatement — no string concatenation of user input.
 */
public class CartDao {

    private static final Logger log = LogManager.getLogger(CartDao.class);

    private final DataSource ds;

    public CartDao(DataSource ds) {
        this.ds = ds;
    }

    // ── Cart resolution ───────────────────────────────────────────────────

    /**
     * Returns the cart UUID for the given user, creating one if it doesn't exist.
     */
    public String getOrCreateCartId(String userId) throws SQLException {
        String selectSql = "SELECT id FROM carts WHERE user_id = ?::uuid";
        String insertSql = "INSERT INTO carts (user_id) VALUES (?::uuid) RETURNING id";

        try (Connection conn = ds.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("id");
                    }
                }
            }
            // No cart yet — create one
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("id");
                    }
                }
            }
        }
        throw new IllegalStateException("Failed to get or create cart for user: " + userId);
    }

    // ── Read ──────────────────────────────────────────────────────────────

    /**
     * Returns all cart items for the user, joined with product data.
     */
    public List<CartItem> getCartItems(String userId) throws SQLException {
        String sql = """
                SELECT ci.cart_id, ci.product_id, ci.quantity, ci.added_at,
                       p.name, p.slug, p.sku, p.weight_grams, p.price_paise, p.mrp_paise, p.images
                FROM cart_items ci
                JOIN carts c ON c.id = ci.cart_id
                JOIN products p ON p.id = ci.product_id
                WHERE c.user_id = ?::uuid
                ORDER BY ci.added_at DESC
                """;

        List<CartItem> items = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapCartItem(rs));
                }
            }
        }
        return items;
    }

    /**
     * Returns the total number of units across all items in the user's cart.
     */
    public int getItemCount(String userId) throws SQLException {
        String sql = """
                SELECT SUM(ci.quantity)
                FROM cart_items ci
                JOIN carts c ON c.id = ci.cart_id
                WHERE c.user_id = ?::uuid
                """;

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int sum = rs.getInt(1);
                    return rs.wasNull() ? 0 : sum;
                }
            }
        }
        return 0;
    }

    // ── Write ─────────────────────────────────────────────────────────────

    /**
     * Inserts the item or updates its quantity if it already exists in the cart.
     * Also bumps the cart's {@code updated_at} timestamp.
     */
    public void addOrUpdateItem(String userId, String productId, int quantity) throws SQLException {
        String cartId = getOrCreateCartId(userId);

        String upsertSql = """
                INSERT INTO cart_items (cart_id, product_id, quantity)
                VALUES (?::uuid, ?::uuid, ?)
                ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = EXCLUDED.quantity
                """;

        String touchSql = "UPDATE carts SET updated_at = NOW() WHERE id = ?::uuid";

        try (Connection conn = ds.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                ps.setString(1, cartId);
                ps.setString(2, productId);
                ps.setInt(3, quantity);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(touchSql)) {
                ps.setString(1, cartId);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Removes a single product from the user's cart.
     */
    public void removeItem(String userId, String productId) throws SQLException {
        String sql = """
                DELETE FROM cart_items
                WHERE cart_id = (SELECT id FROM carts WHERE user_id = ?::uuid)
                  AND product_id = ?::uuid
                """;

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, productId);
            ps.executeUpdate();
        }
    }

    /**
     * Removes all items from the user's cart.
     */
    public void clearCart(String userId) throws SQLException {
        String sql = """
                DELETE FROM cart_items
                WHERE cart_id = (SELECT id FROM carts WHERE user_id = ?::uuid)
                """;

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
    }

    // ── Mapper ────────────────────────────────────────────────────────────

    private static CartItem mapCartItem(ResultSet rs) throws SQLException {
        CartItem ci = new CartItem();
        ci.setCartId(rs.getString("cart_id"));
        ci.setProductId(rs.getString("product_id"));
        ci.setQuantity(rs.getInt("quantity"));

        Timestamp addedAt = rs.getTimestamp("added_at");
        if (addedAt != null) {
            ci.setAddedAt(addedAt.toInstant().atOffset(ZoneOffset.UTC));
        }

        ci.setProductName(rs.getString("name"));
        ci.setProductSlug(rs.getString("slug"));
        ci.setProductSku(rs.getString("sku"));
        ci.setWeightGrams(rs.getInt("weight_grams"));
        ci.setPricePaise(rs.getInt("price_paise"));
        ci.setMrpPaise(rs.getInt("mrp_paise"));

        // images: JSONB stored as text — take first element as imageUrl
        String imagesJson = rs.getString("images");
        String imageUrl = null;
        if (imagesJson != null && !imagesJson.isBlank()) {
            String[] arr = JsonUtil.GSON.fromJson(imagesJson, String[].class);
            if (arr != null && arr.length > 0) {
                imageUrl = arr[0];
            }
        }
        ci.setImageUrl(imageUrl);

        return ci;
    }
}
