package com.budgettracker.persistence;

import com.budgettracker.domain.User;
import com.budgettracker.util.Dates;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcUserRepository.class);

    @Override
    public User save(User user) {
        String sql = "INSERT INTO users (email, display_name, password_hash, base_currency, default_zone_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
        } catch (SQLException e) {
            throw new DataAccessException("save user failed", e);
        }
        return user;
    }

    @Override
    public Optional<User> findById(long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("findById user failed", e);
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByEmail user failed", e);
        }
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users";
        List<User> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("findAll users failed", e);
        }
        return list;
    }

    @Override
    public void update(User user) {
        String sql = "UPDATE users SET display_name = ?, base_currency = ?, default_zone_id = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setString(1, user.getDisplayName());
            ps.setString(2, user.getBaseCurrency());
            ps.setString(3, user.getDefaultZoneId());
            ps.setTimestamp(4, Dates.toTimestamp(user.getUpdatedAt()));
            ps.setLong(5, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("update user failed", e);
        }
    }

    @Override
    public void deleteById(long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("deleteById user failed", e);
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
