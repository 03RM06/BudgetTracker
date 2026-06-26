package com.budgettracker.persistence;

import com.budgettracker.domain.User;
import com.budgettracker.util.Dates;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcUserRepository implements UserRepository {

    @Override
    public User save(User user) throws SQLException {
        String sql = "INSERT INTO users (email, display_name, password_hash, base_currency, default_zone_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getEmail());
            ps.setString(2, user.getDisplayName());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getBaseCurrency());
            ps.setString(5, user.getDefaultZoneId());
            ps.setTimestamp(6, Dates.toTimestamp(user.getCreatedAt()));
            ps.setTimestamp(7, Dates.toTimestamp(user.getUpdatedAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getLong(1));
                }
            }
        }
        return user;
    }

    @Override
    public Optional<User> findById(long id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<User> findByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM users WHERE email = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<User> findAll() throws SQLException {
        String sql = "SELECT * FROM users";
        Connection conn = Database.getConnection();
        List<User> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    @Override
    public void update(User user) throws SQLException {
        String sql = "UPDATE users SET display_name = ?, base_currency = ?, default_zone_id = ?, updated_at = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getDisplayName());
            ps.setString(2, user.getBaseCurrency());
            ps.setString(3, user.getDefaultZoneId());
            ps.setTimestamp(4, Dates.toTimestamp(user.getUpdatedAt()));
            ps.setLong(5, user.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteById(long id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setEmail(rs.getString("email"));
        user.setDisplayName(rs.getString("display_name"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setBaseCurrency(rs.getString("base_currency"));
        user.setDefaultZoneId(rs.getString("default_zone_id"));
        user.setCreatedAt(Dates.toInstant(rs.getTimestamp("created_at")));
        user.setUpdatedAt(Dates.toInstant(rs.getTimestamp("updated_at")));
        return user;
    }
}
