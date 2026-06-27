package com.budgettracker.persistence;

import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(long id);
    List<Transaction> findByUserId(long userId);
    List<Transaction> findByAccountId(long accountId);
    List<Transaction> findByUserIdAndDateRange(long userId, LocalDate from, LocalDate to);
    List<Transaction> findByUserIdCategoryAndDateRange(long userId, long categoryId, LocalDate from, LocalDate to);
    List<Transaction> findByRecurringId(long recurringId);
    List<Transaction> findTransferCreditsForAccount(long accountId);
    void update(Transaction transaction);
    void deleteById(long id);
    default List<Transaction> findByUserIdAndStatus(long userId, TxStatus status) {
        throw new UnsupportedOperationException("findByUserIdAndStatus not implemented");
    }
}
