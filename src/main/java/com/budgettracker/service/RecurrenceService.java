package com.budgettracker.service;

import com.budgettracker.domain.BudgetTrackerException;
import com.budgettracker.domain.Frequency;
import com.budgettracker.domain.RecurringTransaction;
import com.budgettracker.domain.Transaction;
import com.budgettracker.persistence.RecurringTransactionRepository;
import com.budgettracker.persistence.TransactionRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

public class RecurrenceService {

    private final RecurringTransactionRepository recurringRepo;
    private final TransactionRepository txRepo;
    private final TransactionService txService;

    public RecurrenceService(RecurringTransactionRepository recurringRepo,
                             TransactionRepository txRepo,
                             TransactionService txService) {
        this.recurringRepo = recurringRepo;
        this.txRepo = txRepo;
        this.txService = txService;
    }

    public void materializeDue(long userId) {
        LocalDate today = LocalDate.now();
        try {
            List<RecurringTransaction> dueRules = recurringRepo.findDueByDate(today);
            for (RecurringTransaction rule : dueRules) {
                if (rule.getUserId() != userId) {
                    continue;
                }
                // Only count rows on the rule's own account to avoid paired-transfer double counting.
                List<Transaction> existingTxns = txRepo.findByRecurringId(rule.getId())
                        .stream()
                        .filter(t -> t.getAccountId() == rule.getAccountId())
                        .collect(Collectors.toList());
                int existingCount = existingTxns.size();

                LocalDate cursor = rule.getNextRunDate() != null
                        ? rule.getNextRunDate()
                        : rule.getAnchorDate();

                while (!cursor.isAfter(today)) {
                    if (rule.getEndDate() != null && cursor.isAfter(rule.getEndDate())) {
                        break;
                    }
                    if (rule.getMaxOccurrences() != null
                            && existingCount >= rule.getMaxOccurrences()) {
                        break;
                    }
                    final LocalDate pendingDate = cursor;
                    boolean alreadyMaterialized = existingTxns.stream()
                            .anyMatch(t -> t.getOccurredOn().equals(pendingDate));
                    if (!alreadyMaterialized) {
                        Transaction tx = buildTransaction(rule, pendingDate);
                        txService.addTransaction(tx);
                        existingCount++;
                    }
                    LocalDate next = advanceDate(rule, cursor);
                    recurringRepo.updateNextRunDate(rule.getId(), next);
                    cursor = next;
                }
            }
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to materialize recurring transactions", e);
        }
    }

    private Transaction buildTransaction(RecurringTransaction rule, LocalDate pendingDate) {
        Transaction tx = new Transaction();
        tx.setUserId(rule.getUserId());
        tx.setAccountId(rule.getAccountId());
        tx.setCategoryId(rule.getCategoryId());
        tx.setDirection(rule.getDirection());
        tx.setAmount(rule.getTemplateAmount());
        tx.setCurrency(rule.getCurrency());
        tx.setOccurredOn(pendingDate);
        tx.setIncomeNature(rule.getIncomeNature());
        tx.setDescription(rule.getDescription());
        tx.setRecurringId(rule.getId());
        Instant now = Instant.now();
        tx.setOccurredAt(now);
        tx.setCreatedAt(now);
        tx.setUpdatedAt(now);
        return tx;
    }

    LocalDate advanceDate(RecurringTransaction rule, LocalDate from) {
        int intervalCount = rule.getIntervalCount();
        LocalDate advanced;
        switch (rule.getFrequency()) {
            case DAILY:
                advanced = from.plusDays(intervalCount);
                break;
            case WEEKLY:
                advanced = from.plusWeeks(intervalCount);
                break;
            case BIWEEKLY:
                advanced = from.plusWeeks((long) intervalCount * 2);
                break;
            case MONTHLY:
                advanced = from.plusMonths(intervalCount);
                break;
            case QUARTERLY:
                advanced = from.plusMonths((long) intervalCount * 3);
                break;
            case YEARLY:
                advanced = from.plusYears(intervalCount);
                break;
            default:
                advanced = from.plusMonths(intervalCount);
        }
        if ((rule.getFrequency() == Frequency.MONTHLY
                || rule.getFrequency() == Frequency.QUARTERLY
                || rule.getFrequency() == Frequency.YEARLY)
                && rule.getDayOfMonth() != null) {
            YearMonth ym = YearMonth.from(advanced);
            int clampedDay = Math.min(rule.getDayOfMonth(), ym.lengthOfMonth());
            advanced = LocalDate.of(ym.getYear(), ym.getMonthValue(), clampedDay);
        }
        return advanced;
    }
}
