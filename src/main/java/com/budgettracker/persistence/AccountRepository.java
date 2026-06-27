package com.budgettracker.persistence;

import com.budgettracker.domain.Account;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    Account save(Account account);
    Optional<Account> findById(long id);
    List<Account> findByUserId(long userId);
    List<Account> findActiveByUserId(long userId);
    void update(Account account);
    void updateBalance(long accountId, BigDecimal newBalance);
    void deleteById(long id);
}
