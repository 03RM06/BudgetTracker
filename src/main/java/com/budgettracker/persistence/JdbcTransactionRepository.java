package com.budgettracker.persistence;

import com.budgettracker.domain.IncomeNature;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.util.Dates;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcTransactionRepository implements TransactionRepository {

    @Override
    public Transaction save(Transaction tx) throws SQLException {
        String sql = "INSERT INTO transactions (user_id, account_id, category_id, direction, amount, currency, occurred_on, occurred_at, income_nature, description, recurring_id, exchange_rate, transfer_account_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, tx.getUserId());
            ps.setLong(2, tx.getAccountId());
            if (tx.getCategoryId() == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, tx.getCategoryId());
            }
            ps.setString(4, tx.getDirection().name());
            ps.setBigDecimal(5, tx.getAmount());
            ps.setString(6, tx.getCurrency());
            ps.setDate(7, Dates.toSqlDate(tx.getOccurredOn()));
            if (tx.getOccurredAt() == null) {
                ps.setNull(8, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(8, Dates.toTimestamp(tx.getOccurredAt()));
            }
            if (tx.getIncomeNature() == null) {
                ps.setNull(9, Types.VARCHAR);
            } else {
                ps.setString(9, tx.getIncomeNature().name());
            }
            if (tx.getDescription() == null) {
                ps.setNull(10, Types.VARCHAR);
            } else {
                ps.setString(10, tx.getDescription());
            }
            if (tx.getRecurringId() == null) {
                ps.setNull(11, Types.BIGINT);
            } else {
                ps.setLong(11, tx.getRecurringId());
            }
            if (tx.getExchangeRate() == null) {
                ps.setNull(12, Types.DECIMAL);
            } else {
                ps.setBigDecimal(12, tx.getExchangeRate());
            }
            if (tx.getTransferAccountId() == null) {
                ps.setNull(13, Types.BIGINT);
            } else {
                ps.setLong(13, tx.getTransferAccountId());
            }
            ps.setTimestamp(14, Dates.toTimestamp(tx.getCreatedAt()));
            ps.setTimestamp(15, Dates.toTimestamp(tx.getUpdatedAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    tx.setId(keys.getLong(1));
                }
            }
        }
        return tx;
    }

    @Override
    public Optional<Transaction> findById(long id) throws SQLException {
        String sql = "SELECT * FROM transactions WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Transaction> findByUserId(long userId) throws SQLException {
        String sql = "SELECT * FROM transactions WHERE user_id = ? ORDER BY occurred_on DESC";
        Connection conn = Database.getConnection();
        List<Transaction> list = new ArrayList<>();
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
    public List<Transaction> findByAccountId(long accountId) throws SQLException {
        String sql = "SELECT * FROM transactions WHERE account_id = ? ORDER BY occurred_on DESC";
        Connection conn = Database.getConnection();
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<Transaction> findByUserIdAndDateRange(long userId, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT * FROM transactions WHERE user_id = ? AND occurred_on BETWEEN ? AND ? ORDER BY occurred_on DESC";
        Connection conn = Database.getConnection();
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setDate(2, Dates.toSqlDate(from));
            ps.setDate(3, Dates.toSqlDate(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<Transaction> findByUserIdCategoryAndDateRange(long userId, long categoryId, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT * FROM transactions WHERE user_id = ? AND category_id = ? AND occurred_on BETWEEN ? AND ? ORDER BY occurred_on DESC";
        Connection conn = Database.getConnection();
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, categoryId);
            ps.setDate(3, Dates.toSqlDate(from));
            ps.setDate(4, Dates.toSqlDate(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<Transaction> findByRecurringId(long recurringId) throws SQLException {
        String sql = "SELECT * FROM transactions WHERE recurring_id = ?";
        Connection conn = Database.getConnection();
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, recurringId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<Transaction> findTransferCreditsForAccount(long accountId) throws SQLException {
        String sql = "SELECT * FROM transactions WHERE transfer_account_id = ? AND direction = 'TRANSFER'";
        Connection conn = Database.getConnection();
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public void update(Transaction tx) throws SQLException {
        String sql = "UPDATE transactions SET direction = ?, amount = ?, currency = ?, occurred_on = ?, occurred_at = ?, income_nature = ?, description = ?, category_id = ?, transfer_account_id = ?, exchange_rate = ?, updated_at = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tx.getDirection().name());
            ps.setBigDecimal(2, tx.getAmount());
            ps.setString(3, tx.getCurrency());
            ps.setDate(4, Dates.toSqlDate(tx.getOccurredOn()));
            if (tx.getOccurredAt() == null) {
                ps.setNull(5, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(5, Dates.toTimestamp(tx.getOccurredAt()));
            }
            if (tx.getIncomeNature() == null) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, tx.getIncomeNature().name());
            }
            if (tx.getDescription() == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, tx.getDescription());
            }
            if (tx.getCategoryId() == null) {
                ps.setNull(8, Types.BIGINT);
            } else {
                ps.setLong(8, tx.getCategoryId());
            }
            if (tx.getTransferAccountId() == null) {
                ps.setNull(9, Types.BIGINT);
            } else {
                ps.setLong(9, tx.getTransferAccountId());
            }
            if (tx.getExchangeRate() == null) {
                ps.setNull(10, Types.DECIMAL);
            } else {
                ps.setBigDecimal(10, tx.getExchangeRate());
            }
            ps.setTimestamp(11, Dates.toTimestamp(tx.getUpdatedAt()));
            ps.setLong(12, tx.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteById(long id) throws SQLException {
        String sql = "DELETE FROM transactions WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private Transaction mapRow(ResultSet rs) throws SQLException {
        Transaction tx = new Transaction();
        tx.setId(rs.getLong("id"));
        tx.setUserId(rs.getLong("user_id"));
        tx.setAccountId(rs.getLong("account_id"));
        long categoryId = rs.getLong("category_id");
        tx.setCategoryId(rs.wasNull() ? null : categoryId);
        tx.setDirection(TxDirection.valueOf(rs.getString("direction")));
        tx.setAmount(rs.getBigDecimal("amount"));
        tx.setCurrency(rs.getString("currency"));
        tx.setOccurredOn(Dates.toLocalDate(rs.getDate("occurred_on")));
        tx.setOccurredAt(Dates.toInstant(rs.getTimestamp("occurred_at")));
        String incomeNature = rs.getString("income_nature");
        tx.setIncomeNature(incomeNature == null ? null : IncomeNature.valueOf(incomeNature));
        tx.setDescription(rs.getString("description"));
        long recurringId = rs.getLong("recurring_id");
        tx.setRecurringId(rs.wasNull() ? null : recurringId);
        long transferAccountId = rs.getLong("transfer_account_id");
        tx.setTransferAccountId(rs.wasNull() ? null : transferAccountId);
        tx.setExchangeRate(rs.getBigDecimal("exchange_rate"));
        tx.setCreatedAt(Dates.toInstant(rs.getTimestamp("created_at")));
        tx.setUpdatedAt(Dates.toInstant(rs.getTimestamp("updated_at")));
        return tx;
    }
}
