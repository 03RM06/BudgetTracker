package com.budgettracker.service;

import com.budgettracker.domain.BudgetEnvelope;
import com.budgettracker.domain.BudgetStatus;
import com.budgettracker.domain.BudgetTrackerException;
import com.budgettracker.domain.DuplicateResourceException;
import com.budgettracker.domain.PeriodType;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.persistence.BudgetEnvelopeRepository;
import com.budgettracker.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class BudgetService {

    private final BudgetEnvelopeRepository envelopeRepo;
    private final TransactionRepository txRepo;

    public BudgetService(BudgetEnvelopeRepository envelopeRepo, TransactionRepository txRepo) {
        this.envelopeRepo = envelopeRepo;
        this.txRepo = txRepo;
    }

    public EnvelopeStatus evaluate(BudgetEnvelope envelope) {
        BigDecimal spent = computeSpent(envelope);
        BigDecimal effectiveLimit;
        if (envelope.isRollover()) {
            Optional<BudgetEnvelope> prior = findPriorEnvelope(envelope);
            if (prior.isPresent()) {
                BigDecimal priorSpent = computeSpent(prior.get());
                BigDecimal priorRemaining = prior.get().getLimitAmount()
                        .subtract(priorSpent)
                        .max(BigDecimal.ZERO);
                effectiveLimit = envelope.getLimitAmount().add(priorRemaining);
            } else {
                effectiveLimit = envelope.getLimitAmount();
            }
        } else {
            effectiveLimit = envelope.getLimitAmount();
        }
        BigDecimal remaining = effectiveLimit.subtract(spent);
        BudgetStatus status = determineStatus(spent, effectiveLimit, envelope.getAlertThresholdPct());
        return new EnvelopeStatus(envelope, spent, effectiveLimit, remaining, status);
    }

    public List<EnvelopeStatus> evaluateEnvelopes(long userId, long categoryId, LocalDate date) {
        try {
            List<BudgetEnvelope> active = envelopeRepo.findActiveByUserIdAndDate(userId, date);
            List<EnvelopeStatus> results = new ArrayList<>();
            for (BudgetEnvelope envelope : active) {
                if (envelope.getCategoryId() == categoryId) {
                    results.add(evaluate(envelope));
                }
            }
            return results;
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to evaluate envelopes", e);
        }
    }

    public BudgetEnvelope createEnvelope(long userId, long categoryId, String name,
                                         PeriodType periodType, LocalDate periodStart,
                                         BigDecimal limitAmount, boolean rollover,
                                         BigDecimal alertThresholdPct, String zoneId) {
        if (limitAmount == null || limitAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Limit amount must be > 0");
        }
        if (periodType == PeriodType.CUSTOM) {
            throw new IllegalArgumentException(
                    "CUSTOM period requires explicit periodEnd — use createEnvelopeCustom");
        }
        LocalDate periodEnd = computePeriodEnd(periodType, periodStart);
        return saveEnvelope(userId, categoryId, name, periodType, periodStart, periodEnd,
                limitAmount, rollover, alertThresholdPct, zoneId);
    }

    public BudgetEnvelope createEnvelopeCustom(long userId, long categoryId, String name,
                                               LocalDate periodStart, LocalDate periodEnd,
                                               BigDecimal limitAmount, boolean rollover,
                                               BigDecimal alertThresholdPct, String zoneId) {
        if (limitAmount == null || limitAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Limit amount must be > 0");
        }
        return saveEnvelope(userId, categoryId, name, PeriodType.CUSTOM, periodStart, periodEnd,
                limitAmount, rollover, alertThresholdPct, zoneId);
    }

    private BudgetEnvelope saveEnvelope(long userId, long categoryId, String name,
                                        PeriodType periodType, LocalDate periodStart,
                                        LocalDate periodEnd, BigDecimal limitAmount,
                                        boolean rollover, BigDecimal alertThresholdPct,
                                        String zoneId) {
        try {
            if (envelopeRepo.findByCategoryAndPeriodStart(userId, categoryId, periodStart).isPresent()) {
                throw new DuplicateResourceException(
                        "A budget envelope for this category and period start already exists");
            }
            BudgetEnvelope envelope = new BudgetEnvelope();
            envelope.setUserId(userId);
            envelope.setCategoryId(categoryId);
            envelope.setName(name);
            envelope.setPeriodType(periodType);
            envelope.setPeriodStart(periodStart);
            envelope.setPeriodEnd(periodEnd);
            envelope.setLimitAmount(limitAmount);
            envelope.setRollover(rollover);
            envelope.setAlertThresholdPct(alertThresholdPct);
            envelope.setZoneId(zoneId);
            envelope.setActive(true);
            Instant now = Instant.now();
            envelope.setCreatedAt(now);
            envelope.setUpdatedAt(now);
            return envelopeRepo.save(envelope);
        } catch (DuplicateResourceException e) {
            throw e;
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to create budget envelope", e);
        }
    }

    private BigDecimal computeSpent(BudgetEnvelope envelope) {
        try {
            List<Transaction> txns = txRepo.findByUserIdCategoryAndDateRange(
                    envelope.getUserId(), envelope.getCategoryId(),
                    envelope.getPeriodStart(), envelope.getPeriodEnd());
            return txns.stream()
                    .filter(t -> t.getDirection() == TxDirection.EXPENSE)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to compute spending for envelope", e);
        }
    }

    private Optional<BudgetEnvelope> findPriorEnvelope(BudgetEnvelope envelope) {
        try {
            return envelopeRepo.findByUserId(envelope.getUserId()).stream()
                    .filter(e -> e.getCategoryId() == envelope.getCategoryId()
                            && e.getId() != envelope.getId()
                            && e.getPeriodEnd().isBefore(envelope.getPeriodStart()))
                    .max(Comparator.comparing(BudgetEnvelope::getPeriodEnd));
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to find prior envelope", e);
        }
    }

    private BudgetStatus determineStatus(BigDecimal spent, BigDecimal effectiveLimit,
                                         BigDecimal alertThresholdPct) {
        if (spent.compareTo(effectiveLimit) > 0) {
            return BudgetStatus.OVER;
        }
        if (effectiveLimit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal usagePct = spent
                    .divide(effectiveLimit, 4, RoundingMode.HALF_EVEN)
                    .multiply(new BigDecimal("100"));
            if (usagePct.compareTo(alertThresholdPct) >= 0) {
                return BudgetStatus.WARN;
            }
        }
        return BudgetStatus.OK;
    }

    private LocalDate computePeriodEnd(PeriodType periodType, LocalDate periodStart) {
        switch (periodType) {
            case WEEKLY:
                return periodStart.plusDays(6);
            case MONTHLY:
                return YearMonth.from(periodStart).atEndOfMonth();
            case YEARLY:
                return LocalDate.of(periodStart.getYear(), 12, 31);
            default:
                throw new IllegalArgumentException("Unsupported period type: " + periodType);
        }
    }
}
