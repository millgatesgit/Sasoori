package com.sasoori.dao;

import com.sasoori.model.Category;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data-access for the {@code categories} table.
 */
public class CategoryDao {

    private static final Logger log = LogManager.getLogger(CategoryDao.class);

    private final DataSource ds;

    public CategoryDao(DataSource ds) {
        this.ds = ds;
    }

    // ── Queries ───────────────────────────────────────────────────────────

    /**
     * Returns all active categories ordered by sort_order ascending.
     */
    public List<Category> findAllActive() throws SQLException {
        String sql = "SELECT id, name, slug, description, image_url, sort_order, is_active " +
                     "FROM categories WHERE is_active = TRUE ORDER BY sort_order";
        List<Category> results = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(map(rs));
            }
        }
        return results;
    }

    /**
     * Finds an active category by its slug.
     */
    public Optional<Category> findBySlug(String slug) throws SQLException {
        String sql = "SELECT id, name, slug, description, image_url, sort_order, is_active " +
                     "FROM categories WHERE slug = ? AND is_active = TRUE";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, slug);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    // ── Mapper ────────────────────────────────────────────────────────────

    private static Category map(ResultSet rs) throws SQLException {
        Category cat = new Category();
        cat.setId(rs.getInt("id"));
        cat.setName(rs.getString("name"));
        cat.setSlug(rs.getString("slug"));
        cat.setDescription(rs.getString("description"));
        cat.setImageUrl(rs.getString("image_url"));
        cat.setSortOrder(rs.getInt("sort_order"));
        cat.setActive(rs.getBoolean("is_active"));
        return cat;
    }
}
