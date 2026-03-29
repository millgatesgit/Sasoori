package com.sasoori.dao;

import com.sasoori.model.Product;
import com.sasoori.util.JsonUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Data-access for the {@code products} table.
 * All queries use PreparedStatement — no string concatenation in SQL.
 */
public class ProductDao {

    private static final Logger log = LogManager.getLogger(ProductDao.class);

    /** Result type for paginated product listings. */
    public record ProductPage(List<Product> products, int total, int page, int size) {}

    private static final int MAX_SIZE = 50;

    // Column list shared between SELECT queries
    private static final String PRODUCT_COLS =
            "p.id, p.name, p.slug, p.sku, p.description, p.ingredients, " +
            "p.weight_grams, p.price_paise, p.mrp_paise, p.stock_qty, " +
            "p.images, p.tags, p.is_active, p.is_featured, p.created_at, p.updated_at, " +
            "c.id AS category_id, c.name AS category_name, c.slug AS category_slug";

    private final DataSource ds;

    public ProductDao(DataSource ds) {
        this.ds = ds;
    }

    // ── Queries ───────────────────────────────────────────────────────────

    /**
     * Paginated product listing with optional filters and sort.
     * categorySlug, search, and tag may each be null to omit that filter.
     */
    public ProductPage list(String categorySlug, String search, String tag,
                            String sort, int page, int size) throws SQLException {
        size = Math.min(size, MAX_SIZE);
        if (page < 1) page = 1;

        // Build WHERE clauses dynamically (no string concat of user input)
        List<String> conditions = new ArrayList<>();
        List<Object> params     = new ArrayList<>();

        conditions.add("p.is_active = TRUE");

        if (categorySlug != null && !categorySlug.isBlank()) {
            conditions.add("c.slug = ?");
            params.add(categorySlug);
        }
        if (search != null && !search.isBlank()) {
            conditions.add("to_tsvector('english', p.name || ' ' || COALESCE(p.description,'')) " +
                           "@@ plainto_tsquery('english', ?)");
            params.add(search);
        }
        if (tag != null && !tag.isBlank()) {
            conditions.add("? = ANY(p.tags)");
            params.add(tag);
        }

        String where = "WHERE " + String.join(" AND ", conditions);

        String orderBy = switch (sort == null ? "default" : sort) {
            case "price_asc"  -> "ORDER BY p.price_paise ASC";
            case "price_desc" -> "ORDER BY p.price_paise DESC";
            case "newest"     -> "ORDER BY p.created_at DESC";
            default           -> "ORDER BY p.is_featured DESC, p.name ASC";
        };

        String countSql = "SELECT COUNT(*) FROM products p " +
                          "JOIN categories c ON c.id = p.category_id " + where;

        String selectSql = "SELECT " + PRODUCT_COLS + " " +
                           "FROM products p " +
                           "JOIN categories c ON c.id = p.category_id " +
                           where + " " + orderBy + " " +
                           "LIMIT ? OFFSET ?";

        int offset = (page - 1) * size;
        int total;

        try (Connection c = ds.getConnection()) {
            // Count
            try (PreparedStatement ps = c.prepareStatement(countSql)) {
                bindParams(ps, params, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getInt(1);
                }
            }

            // Data
            List<Product> products = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(selectSql)) {
                int idx = bindParams(ps, params, 1);
                ps.setInt(idx++, size);
                ps.setInt(idx,   offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) products.add(map(rs));
                }
            }

            return new ProductPage(products, total, page, size);
        }
    }

    /**
     * Finds an active product by slug.
     */
    public Optional<Product> findBySlug(String slug) throws SQLException {
        String sql = "SELECT " + PRODUCT_COLS + " " +
                     "FROM products p " +
                     "JOIN categories c ON c.id = p.category_id " +
                     "WHERE p.slug = ? AND p.is_active = TRUE";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, slug);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    /**
     * Finds an active product by its UUID (public use — inactive products are excluded).
     */
    public Optional<Product> findById(String id) throws SQLException {
        String sql = "SELECT " + PRODUCT_COLS + " " +
                     "FROM products p " +
                     "JOIN categories c ON c.id = p.category_id " +
                     "WHERE p.id = ?::uuid AND p.is_active = TRUE";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    /**
     * Finds any product by its UUID regardless of active status (admin use).
     */
    public Optional<Product> findByIdAdmin(String id) throws SQLException {
        String sql = "SELECT " + PRODUCT_COLS + " " +
                     "FROM products p " +
                     "JOIN categories c ON c.id = p.category_id " +
                     "WHERE p.id = ?::uuid";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    /**
     * Inserts a new product row and returns the persisted entity (with generated id).
     */
    public Product create(Product p) throws SQLException {
        String sql = """
            INSERT INTO products
                (category_id, name, slug, sku, description, ingredients,
                 weight_grams, price_paise, mrp_paise, stock_qty,
                 images, tags, is_active, is_featured)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            RETURNING id
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bindProductWrite(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    p.setId(rs.getString("id"));
                }
            }
        }
        // Re-fetch to get joined category columns and server-side defaults
        return findByIdAdmin(p.getId()).orElseThrow(
                () -> new IllegalStateException("Product not found after insert: " + p.getId()));
    }

    /**
     * Updates an existing product and returns the refreshed entity.
     */
    public Product update(Product p) throws SQLException {
        String sql = """
            UPDATE products SET
                category_id   = ?,
                name          = ?,
                slug          = ?,
                sku           = ?,
                description   = ?,
                ingredients   = ?,
                weight_grams  = ?,
                price_paise   = ?,
                mrp_paise     = ?,
                stock_qty     = ?,
                images        = ?::jsonb,
                tags          = ?,
                is_active     = ?,
                is_featured   = ?,
                updated_at    = NOW()
            WHERE id = ?::uuid
            """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int idx = bindProductWrite(ps, p);
            ps.setString(idx, p.getId());
            ps.executeUpdate();
        }
        return findByIdAdmin(p.getId()).orElseThrow(
                () -> new IllegalStateException("Product not found after update: " + p.getId()));
    }

    /**
     * Soft-deletes a product by setting is_active = FALSE.
     */
    public void softDelete(String id) throws SQLException {
        String sql = "UPDATE products SET is_active = FALSE, updated_at = NOW() WHERE id = ?::uuid";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Binds write parameters for INSERT/UPDATE (all product fields except id).
     * Returns the next available parameter index.
     */
    private int bindProductWrite(PreparedStatement ps, Product p) throws SQLException {
        int i = 1;
        ps.setInt(i++, p.getCategoryId());
        ps.setString(i++, p.getName());
        ps.setString(i++, p.getSlug());
        ps.setString(i++, p.getSku());
        ps.setString(i++, p.getDescription());
        ps.setString(i++, p.getIngredients());
        ps.setInt(i++, p.getWeightGrams());
        ps.setInt(i++, p.getPricePaise());
        ps.setInt(i++, p.getMrpPaise());
        ps.setInt(i++, p.getStockQty());

        // images → JSON string
        List<String> images = p.getImages();
        ps.setString(i++, images != null ? JsonUtil.GSON.toJson(images) : "[]");

        // tags → SQL Array
        List<String> tags = p.getTags();
        String[] tagsArr = (tags != null) ? tags.toArray(new String[0]) : new String[0];
        // Use ps.getConnection() without closing it — the connection is owned by the caller
        ps.setArray(i++, ps.getConnection().createArrayOf("text", tagsArr));

        ps.setBoolean(i++, p.isActive());
        ps.setBoolean(i++, p.isFeatured());
        return i;
    }

    /**
     * Binds a list of mixed-type params starting at {@code startIdx}.
     * Returns the next available parameter index.
     */
    private int bindParams(PreparedStatement ps, List<Object> params, int startIdx)
            throws SQLException {
        int i = startIdx;
        for (Object v : params) {
            ps.setString(i++, v.toString());
        }
        return i;
    }

    // ── Mapper ────────────────────────────────────────────────────────────

    private static Product map(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getString("id"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setCategoryName(rs.getString("category_name"));
        p.setCategorySlug(rs.getString("category_slug"));
        p.setName(rs.getString("name"));
        p.setSlug(rs.getString("slug"));
        p.setSku(rs.getString("sku"));
        p.setDescription(rs.getString("description"));
        p.setIngredients(rs.getString("ingredients"));
        p.setWeightGrams(rs.getInt("weight_grams"));
        p.setPricePaise(rs.getInt("price_paise"));
        p.setMrpPaise(rs.getInt("mrp_paise"));
        p.setStockQty(rs.getInt("stock_qty"));
        p.setActive(rs.getBoolean("is_active"));
        p.setFeatured(rs.getBoolean("is_featured"));

        // images: JSONB stored as text
        String imagesJson = rs.getString("images");
        if (imagesJson != null && !imagesJson.isBlank()) {
            String[] arr = JsonUtil.GSON.fromJson(imagesJson, String[].class);
            p.setImages(arr != null ? Arrays.asList(arr) : new ArrayList<>());
        } else {
            p.setImages(new ArrayList<>());
        }

        // tags: TEXT[]
        Array tagsArray = rs.getArray("tags");
        if (tagsArray != null) {
            p.setTags(Arrays.asList((String[]) tagsArray.getArray()));
        } else {
            p.setTags(new ArrayList<>());
        }

        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) p.setCreatedAt(ca.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) p.setUpdatedAt(ua.toInstant().atOffset(ZoneOffset.UTC));

        return p;
    }
}
