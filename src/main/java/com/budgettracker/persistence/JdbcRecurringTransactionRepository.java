package com.budgettracker.persistence;

import com.budgettracker.domain.Frequency;
import com.budgettracker.domain.IncomeNature;
import com.budgettracker.domain.RecurringTransaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.util.Dates;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcRecurringTransactionRepository implements RecurringTransactionRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcRecurringTransactionRepository.class);

    @Override
    public RecurringTransaction save(RecurringTransaction rule) {
        String sql = "INSERT INTO recurring_transactions (user_id, account_id, category_id, direction, template_amount, variable_amount, income_nature, currency, frequency, interval_count, anchor_date, zone_id, day_of_month, next_run_date, end_date, max_occurrences, active, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, rule.getUserId());
            ps.setLong(2, rule.getAccountId());
            if (rule.getCategoryId() == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, rule.getCategoryId());
            }
            ps.setString(4, rule.getDirection().name());
            ps.setBigDecimal(5, rule.getTemplateAmount());
            ps.setBoolean(6, rule.isVariableAmount());
            if (rule.getIncomeNature() == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, rule.getIncomeNature().name());
            }
            ps.setString(8, rule.getCurrency());
            ps.setString(9, rule.getFrequency().name());
            ps.setInt(10, rule.getIntervalCount());
            ps.setDate(11, Dates.toSqlDate(rule.getAnchorDate()));
            ps.setString(12, rule.getZoneId());
            if (rule.getDayOfMonth() == null) {
                ps.setNull(13, Types.INTEGER);
            } else {
                ps.setInt(13, rule.getDayOfMonth());
            }
            if (rule.getNextRunDate() == null) {
                ps.setNull(14, Types.DATE);
            } else {
                ps.setDate(14, Dates.toSqlDate(rule.getNextRunDate()));
            }
            if (rule.getEndDate() == null) {
                ps.setNull(15, Types.DATE);
            } else {
                ps.setDate(15, Dates.toSqlDate(rule.getEndDate()));
            }
            if (rule.getMaxOccurrences() == null) {
                ps.setNull(16, Types.INTEGER);
            } else {
                ps.setInt(16, rule.getMaxOccurrences());
            }
            ps.setBoolean(17, rule.isActive());
            if (rule.getDescription() == null) {
                ps.setNull(18, Types.VARCHAR);
            } else {
                ps.setString(18, rule.getDescription());
            }
            ps.setTimestamp(19, Dates.toTimestamp(rule.getCreatedAt()));
            ps.setTimestamp(20, Dates.toTimestamp(rule.getUpdatedAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    rule.setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("save recurring transaction failed", e);
        }
        return rule;
    }

    @Override
    public Optional<RecurringTransaction> findById(long id) {
        String sql = "SELECT * FROM recurring_transactions WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("findById recurring transaction failed", e);
        }
    }

    @Override
    public List<RecurringTransaction> findByUserId(long userId) {
        String sql = "SELECT * FROM recurring_transactions WHERE user_id = ?";
        List<RecurringTransaction> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByUserId recurring transactions failed", e);
        }
        return list;
    }

    @Override
    public List<RecurringTransaction> findDueByDate(LocalDate date) {
        String sql = "SELECT * FROM recurring_transactions WHERE active = 1 AND (next_run_date IS NULL OR next_run_date <= ?) ORDER BY next_run_date ASC";
        List<RecurringTransaction> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setDate(1, Dates.toSqlDate(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findDueByDate recurring transactions failed", e);
        }
        return list;
    }

    @Override
    public void update(RecurringTransaction rule) {
        String sql = "UPDATE recurring_transactions SET account_id = ?, category_id = ?, direction = ?, template_amount = ?, variable_amount = ?, income_nature = ?, currency = ?, frequency = ?, interval_count = ?, anchor_date = ?, zone_id = ?, day_of_month = ?, next_run_date = ?, end_date = ?, max_occurrences = ?, active = ?, description = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, rule.getAccountId());
            if (rule.getCategoryId() == null) {
                ps.setNull(2, Types.BIGINT);
            } else {
                ps.setLong(2, rule.getCategoryId());
            }
            ps.setString(3, rule.getDirection().name());
            ps.setBigDecimal(4, rule.getTemplateAmount());
            ps.setBoolean(5, rule.isVariableAmount());
            if (rule.getIncomeNature() == null) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, rule.getIncomeNature().name());
            }
            ps.setString(7, rule.getCurrency());
            ps.setString(8, rule.getFrequency().name());
            ps.setInt(9, rule.getIntervalCount());
            ps.setDate(10, Dates.toSqlDate(rule.getAnchorDate()));
            ps.setString(11, rule.getZoneId());
            if (rule.getDayOfMonth() == null) {
                ps.setNull(12, Types.INTEGER);
            } else {
                ps.setInt(12, rule.getDayOfMonth());
            }
            if (rule.getNextRunDate() == null) {
                ps.setNull(13, Types.DATE);
            } else {
                ps.setDate(13, Dates.toSqlDate(rule.getNextRunDate()));
            }
            if (rule.getEndDate() == null) {
                ps.setNull(14, Types.DATE);
            } else {
                ps.setDate(14, Dates.toSqlDate(rule.getEndDate()));
            }
            if (rule.getMaxOccurrences() == null) {
                ps.setNull(15, Types.INTEGER);
            } else {
                ps.setInt(15, rule.getMaxOccurrences());
            }
            ps.setBoolean(16, rule.isActive());
            if (rule.getDescription() == null) {
                ps.setNull(17, Types.VARCHAR);
            } else {
                ps.setString(17, rule.getDescription());
            }
            ps.setTimestamp(18, Dates.toTimestamp(rule.getUpdatedAt()));
            ps.setLong(19, rule.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("update recurring transaction failed", e);
        }
    }

    @Override
    public void updateNextRunDate(long id, LocalDate nextRunDate) {
        String sql = "UPDATE recurring_transactions SET next_run_date = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            if (nextRunDate == null) {
                ps.setNull(1, Types.DATE);
            } else {
                ps.setDate(1, Dates.toSqlDate(nextRunDate));
            }
            ps.setTimestamp(2, Dates.toTimestamp(Instant.now()));
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("updateNextRunDate recurring transaction failed", e);
        }
    }

    @Override
    public void deleteById(long id) {
        String sql = "DELETE FROM recurring_transactions WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("deleteById recurring transaction failed", e);
        }
    }

    private RecurringTransaction mapRow(ResultSet rs) throws SQLException {
        RecurringTransaction rule = new RecurringTransaction();
        rule.setId(rs.getLong("id"));
        rule.setUserId(rs.getLong("user_id"));
        rule.setAccountId(rs.getLong("account_id"));
        long categoryId = rs.getLong("category_id");
        rule.setCategoryId(rs.wasNull() ? null : categoryId);
        rule.setDirection(TxDirection.valueOf(rs.getString("direction")));
        rule.setTemplateAmount(rs.getBigDecimal("template_amount"));
        rule.setVariableAmount(rs.getBoolean("variable_amount"));
        String incomeNature = rs.getString("income_nature");
        rule.setIncomeNature(incomeNature == null ? null : IncomeNature.valueOf(incomeNature));
        rule.setCurrency(rs.getString("currency"));
        rule.setFrequency(Frequency.valueOf(rs.getString("frequency")));
        rule.setIntervalCount(rs.getInt("interval_count"));
        rule.setAnchorDate(Dates.toLocalDate(rs.getDate("anchor_date")));
        rule.setZoneId(rs.getString("zone_id"));
        int dayOfMonth = rs.getInt("day_of_month");
        rule.setDayOfMonth(rs.wasNull() ? null : dayOfMonth);
        rule.setNextRunDate(Dates.toLocalDate(rs.getDate("next_run_date")));
        rule.setEndDate(Dates.toLocalDate(rs.getDate("end_date")));
        int maxOccurrences = rs.getInt("max_occurrences");
        rule.setMaxOccurrences(rs.wasNull() ? null : maxOccurrences);
        rule.setActive(rs.getBoolean("active"));
        rule.setDescription(rs.getString("description"));
        rule.setCreatedAt(Dates.toInstant(rs.getTimestamp("created_at")));
        rule.setUpdatedAt(Dates.toInstant(rs.getTimestamp("updated_at")));
        return rule;
    }
}
