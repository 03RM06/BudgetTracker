package com.budgettracker.persistence;

import com.budgettracker.domain.BudgetEnvelope;
import com.budgettracker.domain.PeriodType;
import com.budgettracker.util.Dates;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcBudgetEnvelopeRepository implements BudgetEnvelopeRepository {

    @Override
    public BudgetEnvelope save(BudgetEnvelope envelope) throws SQLException {
        String sql = "INSERT INTO budget_envelopes (user_id, category_id, name, period_type, period_start, period_end, limit_amount, rollover, alert_threshold_pct, zone_id, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
        }
        return envelope;
    }

    @Override
    public Optional<BudgetEnvelope> findById(long id) throws SQLException {
        String sql = "SELECT * FROM budget_envelopes WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<BudgetEnvelope> findByUserId(long userId) throws SQLException {
        String sql = "SELECT * FROM budget_envelopes WHERE user_id = ?";
        Connection conn = Database.getConnection();
        List<BudgetEnvelope> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<BudgetEnvelope> findActiveByUserIdAndDate(long userId, LocalDate date) throws SQLException {
        String sql = "SELECT * FROM budget_envelopes WHERE user_id = ? AND active = 1 AND period_start <= ? AND period_end >= ?";
        Connection conn = Database.getConnection();
        List<BudgetEnvelope> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setDate(2, Dates.toSqlDate(date));
            ps.setDate(3, Dates.toSqlDate(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public Optional<BudgetEnvelope> findByCategoryAndPeriodStart(long userId, long categoryId, LocalDate periodStart) throws SQLException {
        String sql = "SELECT * FROM budget_envelopes WHERE user_id = ? AND category_id = ? AND period_start = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, categoryId);
            ps.setDate(3, Dates.toSqlDate(periodStart));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public void update(BudgetEnvelope envelope) throws SQLException {
        String sql = "UPDATE budget_envelopes SET category_id = ?, name = ?, period_type = ?, period_start = ?, period_end = ?, limit_amount = ?, rollover = ?, alert_threshold_pct = ?, zone_id = ?, active = ?, updated_at = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
        }
    }

    @Override
    public void deleteById(long id) throws SQLException {
        String sql = "DELETE FROM budget_envelopes WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
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
