package com.budgettracker.persistence;

import com.budgettracker.domain.IncomeNature;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.domain.TxStatus;
import com.budgettracker.util.Dates;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcTransactionRepository implements TransactionRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcTransactionRepository.class);

    @Override
    public Transaction save(Transaction tx) {
        String sql = "INSERT INTO transactions (user_id, account_id, category_id, direction, status, amount, currency, occurred_on, occurred_at, income_nature, description, recurring_id, exchange_rate, transfer_account_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, tx.getUserId());
            ps.setLong(2, tx.getAccountId());
            if (tx.getCategoryId() == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, tx.getCategoryId());
            }
            ps.setString(4, tx.getDirection().name());
            ps.setString(5, tx.getStatus() != null ? tx.getStatus().name() : TxStatus.POSTED.name());
            ps.setBigDecimal(6, tx.getAmount());
            ps.setString(7, tx.getCurrency());
            ps.setDate(8, Dates.toSqlDate(tx.getOccurredOn()));
            if (tx.getOccurredAt() == null) {
                ps.setNull(9, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(9, Dates.toTimestamp(tx.getOccurredAt()));
            }
            if (tx.getIncomeNature() == null) {
                ps.setNull(10, Types.VARCHAR);
            } else {
                ps.setString(10, tx.getIncomeNature().name());
            }
            if (tx.getDescription() == null) {
                ps.setNull(11, Types.VARCHAR);
            } else {
                ps.setString(11, tx.getDescription());
            }
            if (tx.getRecurringId() == null) {
                ps.setNull(12, Types.BIGINT);
            } else {
                ps.setLong(12, tx.getRecurringId());
            }
            if (tx.getExchangeRate() == null) {
                ps.setNull(13, Types.DECIMAL);
            } else {
                ps.setBigDecimal(13, tx.getExchangeRate());
            }
            if (tx.getTransferAccountId() == null) {
                ps.setNull(14, Types.BIGINT);
            } else {
                ps.setLong(14, tx.getTransferAccountId());
            }
            ps.setTimestamp(15, Dates.toTimestamp(tx.getCreatedAt()));
            ps.setTimestamp(16, Dates.toTimestamp(tx.getUpdatedAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    tx.setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("save transaction failed", e);
        }
        return tx;
    }

    @Override
    public Optional<Transaction> findById(long id) {
        String sql = "SELECT * FROM transactions WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("findById transaction failed", e);
        }
    }

    @Override
    public List<Transaction> findByUserId(long userId) {
        String sql = "SELECT * FROM transactions WHERE user_id = ? ORDER BY occurred_on DESC";
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByUserId transactions failed", e);
        }
        return list;
    }

    @Override
    public List<Transaction> findByAccountId(long accountId) {
        String sql = "SELECT * FROM transactions WHERE account_id = ? ORDER BY occurred_on DESC";
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByAccountId transactions failed", e);
        }
        return list;
    }

    @Override
    public List<Transaction> findByUserIdAndDateRange(long userId, LocalDate from, LocalDate to) {
        String sql = "SELECT * FROM transactions WHERE user_id = ? AND occurred_on BETWEEN ? AND ? ORDER BY occurred_on DESC";
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setDate(2, Dates.toSqlDate(from));
            ps.setDate(3, Dates.toSqlDate(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByUserIdAndDateRange transactions failed", e);
        }
        return list;
    }

    @Override
    public List<Transaction> findByUserIdCategoryAndDateRange(long userId, long categoryId, LocalDate from, LocalDate to) {
        String sql = "SELECT * FROM transactions WHERE user_id = ? AND category_id = ? AND occurred_on BETWEEN ? AND ? ORDER BY occurred_on DESC";
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, categoryId);
            ps.setDate(3, Dates.toSqlDate(from));
            ps.setDate(4, Dates.toSqlDate(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByUserIdCategoryAndDateRange transactions failed", e);
        }
        return list;
    }

    @Override
    public List<Transaction> findByRecurringId(long recurringId) {
        String sql = "SELECT * FROM transactions WHERE recurring_id = ?";
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, recurringId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByRecurringId transactions failed", e);
        }
        return list;
    }

    @Override
    public List<Transaction> findTransferCreditsForAccount(long accountId) {
        String sql = "SELECT * FROM transactions WHERE transfer_account_id = ? AND direction = 'TRANSFER'";
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findTransferCreditsForAccount transactions failed", e);
        }
        return list;
    }

    @Override
    public List<Transaction> findByUserIdAndStatus(long userId, TxStatus status) {
        String sql = "SELECT * FROM transactions WHERE user_id = ? AND status = ? ORDER BY occurred_on DESC";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<Transaction> result = new ArrayList<>();
                while (rs.next()) result.add(mapRow(rs));
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByUserIdAndStatus failed", e);
        }
    }

    @Override
    public void update(Transaction tx) {
        String sql = "UPDATE transactions SET direction = ?, status = ?, amount = ?, currency = ?, occurred_on = ?, occurred_at = ?, income_nature = ?, description = ?, category_id = ?, transfer_account_id = ?, exchange_rate = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setString(1, tx.getDirection().name());
            ps.setString(2, tx.getStatus() != null ? tx.getStatus().name() : TxStatus.POSTED.name());
            ps.setBigDecimal(3, tx.getAmount());
            ps.setString(4, tx.getCurrency());
            ps.setDate(5, Dates.toSqlDate(tx.getOccurredOn()));
            if (tx.getOccurredAt() == null) {
                ps.setNull(6, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(6, Dates.toTimestamp(tx.getOccurredAt()));
            }
            if (tx.getIncomeNature() == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, tx.getIncomeNature().name());
            }
            if (tx.getDescription() == null) {
                ps.setNull(8, Types.VARCHAR);
            } else {
                ps.setString(8, tx.getDescription());
            }
            if (tx.getCategoryId() == null) {
                ps.setNull(9, Types.BIGINT);
            } else {
                ps.setLong(9, tx.getCategoryId());
            }
            if (tx.getTransferAccountId() == null) {
                ps.setNull(10, Types.BIGINT);
            } else {
                ps.setLong(10, tx.getTransferAccountId());
            }
            if (tx.getExchangeRate() == null) {
                ps.setNull(11, Types.DECIMAL);
            } else {
                ps.setBigDecimal(11, tx.getExchangeRate());
            }
            ps.setTimestamp(12, Dates.toTimestamp(tx.getUpdatedAt()));
            ps.setLong(13, tx.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("update transaction failed", e);
        }
    }

    @Override
    public void deleteById(long id) {
        String sql = "DELETE FROM transactions WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("deleteById transaction failed", e);
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
        String statusStr = rs.getString("status");
        if (statusStr != null) {
            tx.setStatus(TxStatus.valueOf(statusStr));
        }
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
