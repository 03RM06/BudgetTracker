package com.budgettracker.persistence;

import com.budgettracker.domain.Transaction;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    Transaction save(Transaction transaction) throws SQLException;
    Optional<Transaction> findById(long id) throws SQLException;
    List<Transaction> findByUserId(long userId) throws SQLException;
    List<Transaction> findByAccountId(long accountId) throws SQLException;
    List<Transaction> findByUserIdAndDateRange(long userId, LocalDate from, LocalDate to) throws SQLException;
    List<Transaction> findByUserIdCategoryAndDateRange(long userId, long categoryId, LocalDate from, LocalDate to) throws SQLException;
    List<Transaction> findByRecurringId(long recurringId) throws SQLException;
    List<Transaction> findTransferCreditsForAccount(long accountId) throws SQLException;
    void update(Transaction transaction) throws SQLException;
    void deleteById(long id) throws SQLException;
}
