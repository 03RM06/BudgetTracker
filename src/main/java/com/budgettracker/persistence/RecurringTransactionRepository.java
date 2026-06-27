package com.budgettracker.persistence;

import com.budgettracker.domain.RecurringTransaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringTransactionRepository {
    RecurringTransaction save(RecurringTransaction rule);
    Optional<RecurringTransaction> findById(long id);
    List<RecurringTransaction> findByUserId(long userId);
    List<RecurringTransaction> findDueByDate(LocalDate date);
    void update(RecurringTransaction rule);
    void updateNextRunDate(long id, LocalDate nextRunDate);
    void deleteById(long id);
}
