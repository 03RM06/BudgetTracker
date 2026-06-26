package com.budgettracker.persistence;

import com.budgettracker.domain.RecurringTransaction;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringTransactionRepository {
    RecurringTransaction save(RecurringTransaction rule) throws SQLException;
    Optional<RecurringTransaction> findById(long id) throws SQLException;
    List<RecurringTransaction> findByUserId(long userId) throws SQLException;
    List<RecurringTransaction> findDueByDate(LocalDate date) throws SQLException;
    void update(RecurringTransaction rule) throws SQLException;
    void updateNextRunDate(long id, LocalDate nextRunDate) throws SQLException;
    void deleteById(long id) throws SQLException;
}
