package com.sasoori.dao;

import com.sasoori.model.Address;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data-access for the {@code addresses} table.
 * All queries use PreparedStatement — no string concatenation of user input.
 */
public class AddressDao {

    private static final Logger log = LogManager.getLogger(AddressDao.class);

    private final DataSource ds;

    public AddressDao(DataSource ds) {
        this.ds = ds;
    }

    // ── Read ──────────────────────────────────────────────────────────────

    /**
     * Returns all addresses for the given user, default first.
     */
    public List<Address> findByUserId(String userId) throws SQLException {
        String sql = """
                SELECT id, user_id, name, phone, line1, line2, city, state, pincode, is_default
                FROM addresses
                WHERE user_id = ?::uuid
                ORDER BY is_default DESC, id ASC
                """;

        List<Address> list = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapAddress(rs));
                }
            }
        }
        return list;
    }

    /**
     * Finds a specific address by id, verifying it belongs to the given user.
     */
    public Optional<Address> findById(int id, String userId) throws SQLException {
        String sql = """
                SELECT id, user_id, name, phone, line1, line2, city, state, pincode, is_default
                FROM addresses
                WHERE id = ? AND user_id = ?::uuid
                """;

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapAddress(rs));
                }
            }
        }
        return Optional.empty();
    }

    // ── Write ─────────────────────────────────────────────────────────────

    /**
     * Inserts a new address and returns it with the generated id.
     */
    public Address create(Address a) throws SQLException {
        String sql = """
                INSERT INTO addresses (user_id, name, phone, line1, line2, city, state, pincode, is_default)
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id, user_id, name, phone, line1, line2, city, state, pincode, is_default
                """;

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a.getUserId());
            ps.setString(2, a.getName());
            ps.setString(3, a.getPhone());
            ps.setString(4, a.getLine1());
            ps.setString(5, a.getLine2());
            ps.setString(6, a.getCity());
            ps.setString(7, a.getState());
            ps.setString(8, a.getPincode());
            ps.setBoolean(9, a.isDefault());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAddress(rs);
                }
            }
        }
        throw new IllegalStateException("INSERT INTO addresses did not return a row");
    }

    /**
     * Updates an existing address (by id and userId) and returns the updated row.
     */
    public Address update(Address a) throws SQLException {
        String sql = """
                UPDATE addresses
                SET name = ?, phone = ?, line1 = ?, line2 = ?,
                    city = ?, state = ?, pincode = ?, is_default = ?
                WHERE id = ? AND user_id = ?::uuid
                RETURNING id, user_id, name, phone, line1, line2, city, state, pincode, is_default
                """;

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a.getName());
            ps.setString(2, a.getPhone());
            ps.setString(3, a.getLine1());
            ps.setString(4, a.getLine2());
            ps.setString(5, a.getCity());
            ps.setString(6, a.getState());
            ps.setString(7, a.getPincode());
            ps.setBoolean(8, a.isDefault());
            ps.setInt(9, a.getId());
            ps.setString(10, a.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapAddress(rs);
                }
            }
        }
        throw new IllegalStateException("UPDATE addresses returned no row for id=" + a.getId());
    }

    /**
     * Deletes an address belonging to the given user.
     */
    public void delete(int id, String userId) throws SQLException {
        String sql = "DELETE FROM addresses WHERE id = ? AND user_id = ?::uuid";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }

    /**
     * Sets the given address as default for the user in a single transaction:
     * clears all defaults, then sets the target.
     */
    public void setDefault(int id, String userId) throws SQLException {
        String clearSql = "UPDATE addresses SET is_default = FALSE WHERE user_id = ?::uuid";
        String setSql   = "UPDATE addresses SET is_default = TRUE  WHERE id = ? AND user_id = ?::uuid";

        try (Connection conn = ds.getConnection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(clearSql)) {
                    ps.setString(1, userId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(setSql)) {
                    ps.setInt(1, id);
                    ps.setString(2, userId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        }
    }

    // ── Mapper ────────────────────────────────────────────────────────────

    private static Address mapAddress(ResultSet rs) throws SQLException {
        Address a = new Address();
        a.setId(rs.getInt("id"));
        a.setUserId(rs.getString("user_id"));
        a.setName(rs.getString("name"));
        a.setPhone(rs.getString("phone"));
        a.setLine1(rs.getString("line1"));
        a.setLine2(rs.getString("line2"));
        a.setCity(rs.getString("city"));
        a.setState(rs.getString("state"));
        a.setPincode(rs.getString("pincode"));
        a.setDefault(rs.getBoolean("is_default"));
        return a;
    }
}
