package com.budgettracker.persistence;

import com.budgettracker.domain.Account;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    Account save(Account account) throws SQLException;
    Optional<Account> findById(long id) throws SQLException;
    List<Account> findByUserId(long userId) throws SQLException;
    List<Account> findActiveByUserId(long userId) throws SQLException;
    void update(Account account) throws SQLException;
    void updateBalance(long accountId, BigDecimal newBalance) throws SQLException;
    void deleteById(long id) throws SQLException;
}
