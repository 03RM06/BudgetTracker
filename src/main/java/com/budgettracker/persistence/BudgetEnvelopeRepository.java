package com.budgettracker.persistence;

import com.budgettracker.domain.BudgetEnvelope;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetEnvelopeRepository {
    BudgetEnvelope save(BudgetEnvelope envelope) throws SQLException;
    Optional<BudgetEnvelope> findById(long id) throws SQLException;
    List<BudgetEnvelope> findByUserId(long userId) throws SQLException;
    List<BudgetEnvelope> findActiveByUserIdAndDate(long userId, LocalDate date) throws SQLException;
    Optional<BudgetEnvelope> findByCategoryAndPeriodStart(long userId, long categoryId, LocalDate periodStart) throws SQLException;
    void update(BudgetEnvelope envelope) throws SQLException;
    void deleteById(long id) throws SQLException;
}
