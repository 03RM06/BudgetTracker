package com.budgettracker.persistence;

import com.budgettracker.domain.BudgetEnvelope;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetEnvelopeRepository {
    BudgetEnvelope save(BudgetEnvelope envelope);
    Optional<BudgetEnvelope> findById(long id);
    List<BudgetEnvelope> findByUserId(long userId);
    List<BudgetEnvelope> findActiveByUserIdAndDate(long userId, LocalDate date);
    Optional<BudgetEnvelope> findByCategoryAndPeriodStart(long userId, long categoryId, LocalDate periodStart);
    void update(BudgetEnvelope envelope);
    void deleteById(long id);
}
