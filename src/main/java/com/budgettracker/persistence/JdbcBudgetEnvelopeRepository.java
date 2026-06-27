package com.budgettracker.persistence;

import com.budgettracker.domain.BudgetEnvelope;
import com.budgettracker.domain.PeriodType;
import com.budgettracker.util.Dates;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcBudgetEnvelopeRepository implements BudgetEnvelopeRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcBudgetEnvelopeRepository.class);

    @Override
    public BudgetEnvelope save(BudgetEnvelope envelope) {
        String sql = "INSERT INTO budget_envelopes (user_id, category_id, name, period_type, period_start, period_end, limit_amount, rollover, alert_threshold_pct, zone_id, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, envelope.getUserId());
            ps.setLong(2, envelope.getCategoryId());
            ps.setString(3, envelope.getName());
            ps.setString(4, envelope.getPeriodType().name());
            ps.setDate(5, Dates.toSqlDate(envelope.getPeriodStart()));
            ps.setDate(6, Dates.toSqlDate(envelope.getPeriodEnd()));
            ps.setBigDecimal(7, envelope.getLimitAmount());
            ps.setBoolean(8, envelope.isRollover());
            ps.setBigDecimal(9, envelope.getAlertThresholdPct());
            ps.setString(10, envelope.getZoneId());
            ps.setBoolean(11, envelope.isActive());
            ps.setTimestamp(12, Dates.toTimestamp(envelope.getCreatedAt()));
            ps.setTimestamp(13, Dates.toTimestamp(envelope.getUpdatedAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    envelope.setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("save budget envelope failed", e);
        }
        return envelope;
    }

    @Override
    public Optional<BudgetEnvelope> findById(long id) {
        String sql = "SELECT * FROM budget_envelopes WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("findById budget envelope failed", e);
        }
    }

    @Override
    public List<BudgetEnvelope> findByUserId(long userId) {
        String sql = "SELECT * FROM budget_envelopes WHERE user_id = ?";
        List<BudgetEnvelope> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByUserId budget envelopes failed", e);
        }
        return list;
    }

    @Override
    public List<BudgetEnvelope> findActiveByUserIdAndDate(long userId, LocalDate date) {
        String sql = "SELECT * FROM budget_envelopes WHERE user_id = ? AND active = 1 AND period_start <= ? AND period_end >= ?";
        List<BudgetEnvelope> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setDate(2, Dates.toSqlDate(date));
            ps.setDate(3, Dates.toSqlDate(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findActiveByUserIdAndDate budget envelopes failed", e);
        }
        return list;
    }

    @Override
    public Optional<BudgetEnvelope> findByCategoryAndPeriodStart(long userId, long categoryId, LocalDate periodStart) {
        String sql = "SELECT * FROM budget_envelopes WHERE user_id = ? AND category_id = ? AND period_start = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, categoryId);
            ps.setDate(3, Dates.toSqlDate(periodStart));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByCategoryAndPeriodStart budget envelope failed", e);
        }
    }

    @Override
    public void update(BudgetEnvelope envelope) {
        String sql = "UPDATE budget_envelopes SET category_id = ?, name = ?, period_type = ?, period_start = ?, period_end = ?, limit_amount = ?, rollover = ?, alert_threshold_pct = ?, zone_id = ?, active = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, envelope.getCategoryId());
            ps.setString(2, envelope.getName());
            ps.setString(3, envelope.getPeriodType().name());
            ps.setDate(4, Dates.toSqlDate(envelope.getPeriodStart()));
            ps.setDate(5, Dates.toSqlDate(envelope.getPeriodEnd()));
            ps.setBigDecimal(6, envelope.getLimitAmount());
            ps.setBoolean(7, envelope.isRollover());
            ps.setBigDecimal(8, envelope.getAlertThresholdPct());
            ps.setString(9, envelope.getZoneId());
            ps.setBoolean(10, envelope.isActive());
            ps.setTimestamp(11, Dates.toTimestamp(envelope.getUpdatedAt()));
            ps.setLong(12, envelope.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("update budget envelope failed", e);
        }
    }

    @Override
    public void deleteById(long id) {
        String sql = "DELETE FROM budget_envelopes WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("deleteById budget envelope failed", e);
        }
    }

    private BudgetEnvelope mapRow(ResultSet rs) throws SQLException {
        BudgetEnvelope envelope = new BudgetEnvelope();
        envelope.setId(rs.getLong("id"));
        envelope.setUserId(rs.getLong("user_id"));
        envelope.setCategoryId(rs.getLong("category_id"));
        envelope.setName(rs.getString("name"));
        envelope.setPeriodType(PeriodType.valueOf(rs.getString("period_type")));
        envelope.setPeriodStart(Dates.toLocalDate(rs.getDate("period_start")));
        envelope.setPeriodEnd(Dates.toLocalDate(rs.getDate("period_end")));
        envelope.setLimitAmount(rs.getBigDecimal("limit_amount"));
        envelope.setRollover(rs.getBoolean("rollover"));
        envelope.setAlertThresholdPct(rs.getBigDecimal("alert_threshold_pct"));
        envelope.setZoneId(rs.getString("zone_id"));
        envelope.setActive(rs.getBoolean("active"));
        envelope.setCreatedAt(Dates.toInstant(rs.getTimestamp("created_at")));
        envelope.setUpdatedAt(Dates.toInstant(rs.getTimestamp("updated_at")));
        return envelope;
    }
}
