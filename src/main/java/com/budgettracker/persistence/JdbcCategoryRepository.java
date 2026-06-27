package com.budgettracker.persistence;

import com.budgettracker.domain.Category;
import com.budgettracker.domain.CategoryType;
import com.budgettracker.util.Dates;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcCategoryRepository implements CategoryRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcCategoryRepository.class);

    @Override
    public Category save(Category category) {
        String sql = "INSERT INTO categories (user_id, name, category_type, parent_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, category.getUserId());
            ps.setString(2, category.getName());
            ps.setString(3, category.getType().name());
            if (category.getParentId() == null) {
                ps.setNull(4, Types.BIGINT);
            } else {
                ps.setLong(4, category.getParentId());
            }
            ps.setTimestamp(5, Dates.toTimestamp(category.getCreatedAt()));
            ps.setTimestamp(6, Dates.toTimestamp(category.getUpdatedAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    category.setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("save category failed", e);
        }
        return category;
    }

    @Override
    public Optional<Category> findById(long id) {
        String sql = "SELECT * FROM categories WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("findById category failed", e);
        }
    }

    @Override
    public List<Category> findByUserId(long userId) {
        String sql = "SELECT * FROM categories WHERE user_id = ?";
        List<Category> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByUserId categories failed", e);
        }
        return list;
    }

    @Override
    public List<Category> findByUserIdAndType(long userId, CategoryType type) {
        String sql = "SELECT * FROM categories WHERE user_id = ? AND category_type = ?";
        List<Category> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByUserIdAndType categories failed", e);
        }
        return list;
    }

    @Override
    public void update(Category category) {
        String sql = "UPDATE categories SET name = ?, category_type = ?, parent_id = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getType().name());
            if (category.getParentId() == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, category.getParentId());
            }
            ps.setTimestamp(4, Dates.toTimestamp(category.getUpdatedAt()));
            ps.setLong(5, category.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("update category failed", e);
        }
    }

    @Override
    public void deleteById(long id) {
        String sql = "DELETE FROM categories WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("deleteById category failed", e);
        }
    }

    private Category mapRow(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setId(rs.getLong("id"));
        category.setUserId(rs.getLong("user_id"));
        category.setName(rs.getString("name"));
        category.setType(CategoryType.valueOf(rs.getString("category_type")));
        long parentId = rs.getLong("parent_id");
        category.setParentId(rs.wasNull() ? null : parentId);
        category.setCreatedAt(Dates.toInstant(rs.getTimestamp("created_at")));
        category.setUpdatedAt(Dates.toInstant(rs.getTimestamp("updated_at")));
        return category;
    }
}
