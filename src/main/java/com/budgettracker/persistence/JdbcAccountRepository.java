package com.budgettracker.persistence;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.AccountType;
import com.budgettracker.util.Dates;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcAccountRepository implements AccountRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcAccountRepository.class);

    @Override
    public Account save(Account account) {
        String sql = "INSERT INTO accounts (user_id, name, account_type, opening_balance, current_balance, currency, archived, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, account.getUserId());
            ps.setString(2, account.getName());
            ps.setString(3, account.getType().name());
            ps.setBigDecimal(4, account.getOpeningBalance());
            ps.setBigDecimal(5, account.getCurrentBalance());
            ps.setString(6, account.getCurrency());
            ps.setBoolean(7, account.isArchived());
            ps.setTimestamp(8, Dates.toTimestamp(account.getCreatedAt()));
            ps.setTimestamp(9, Dates.toTimestamp(account.getUpdatedAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    account.setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("save account failed", e);
        }
        return account;
    }

    @Override
    public Optional<Account> findById(long id) {
        String sql = "SELECT * FROM accounts WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("findById account failed", e);
        }
    }

    @Override
    public List<Account> findByUserId(long userId) {
        String sql = "SELECT * FROM accounts WHERE user_id = ?";
        List<Account> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByUserId accounts failed", e);
        }
        return list;
    }

    @Override
    public List<Account> findActiveByUserId(long userId) {
        String sql = "SELECT * FROM accounts WHERE user_id = ? AND archived = 0";
        List<Account> list = new ArrayList<>();
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findActiveByUserId accounts failed", e);
        }
        return list;
    }

    @Override
    public void update(Account account) {
        String sql = "UPDATE accounts SET name = ?, account_type = ?, currency = ?, archived = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setString(1, account.getName());
            ps.setString(2, account.getType().name());
            ps.setString(3, account.getCurrency());
            ps.setBoolean(4, account.isArchived());
            ps.setTimestamp(5, Dates.toTimestamp(account.getUpdatedAt()));
            ps.setLong(6, account.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("update account failed", e);
        }
    }

    @Override
    public void updateBalance(long accountId, BigDecimal newBalance) {
        String sql = "UPDATE accounts SET current_balance = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setBigDecimal(1, newBalance);
            ps.setTimestamp(2, Dates.toTimestamp(Instant.now()));
            ps.setLong(3, accountId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("updateBalance account failed", e);
        }
    }

    @Override
    public void deleteById(long id) {
        String sql = "DELETE FROM accounts WHERE id = ?";
        try (PreparedStatement ps = ConnectionContext.requireConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("deleteById account failed", e);
        }
    }

    private Account mapRow(ResultSet rs) throws SQLException {
        Account account = new Account();
        account.setId(rs.getLong("id"));
        account.setUserId(rs.getLong("user_id"));
        account.setName(rs.getString("name"));
        account.setType(AccountType.valueOf(rs.getString("account_type")));
        account.setOpeningBalance(rs.getBigDecimal("opening_balance"));
        account.setCurrentBalance(rs.getBigDecimal("current_balance"));
        account.setCurrency(rs.getString("currency"));
        account.setArchived(rs.getBoolean("archived"));
        account.setCreatedAt(Dates.toInstant(rs.getTimestamp("created_at")));
        account.setUpdatedAt(Dates.toInstant(rs.getTimestamp("updated_at")));
        return account;
    }
}
