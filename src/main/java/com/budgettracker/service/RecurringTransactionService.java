package com.budgettracker.service;

import com.budgettracker.domain.RecurringTransaction;
import com.budgettracker.domain.ResourceNotFoundException;
import com.budgettracker.persistence.RecurringTransactionRepository;
import com.budgettracker.persistence.TxRunner;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class RecurringTransactionService {

    private final RecurringTransactionRepository ruleRepo;
    private final TxRunner txRunner;

    public RecurringTransactionService(RecurringTransactionRepository ruleRepo, TxRunner txRunner) {
        this.ruleRepo = ruleRepo;
        this.txRunner = txRunner;
    }

    public List<RecurringTransaction> getActiveRules(long userId) {
        return txRunner.inTransaction(() -> ruleRepo.findByUserId(userId).stream()
                .filter(RecurringTransaction::isActive)
                .collect(Collectors.toList()));
    }

    public RecurringTransaction createRule(RecurringTransaction rule) {
        return txRunner.inTransaction(() -> ruleRepo.save(rule));
    }

    public void enableRule(long ruleId, long userId) {
        txRunner.inTransaction(() -> {
            RecurringTransaction rule = ruleRepo.findById(ruleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Rule not found: " + ruleId));
            if (rule.getUserId() != userId) {
                throw new SecurityException("Access denied: rule does not belong to current user");
            }
            rule.setActive(true);
            rule.setUpdatedAt(Instant.now());
            ruleRepo.update(rule);
        });
    }

    public void disableRule(long ruleId, long userId) {
        txRunner.inTransaction(() -> {
            RecurringTransaction rule = ruleRepo.findById(ruleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Rule not found: " + ruleId));
            if (rule.getUserId() != userId) {
                throw new SecurityException("Access denied: rule does not belong to current user");
            }
            rule.setActive(false);
            rule.setUpdatedAt(Instant.now());
            ruleRepo.update(rule);
        });
    }

    public void deleteRule(long ruleId, long userId) {
        txRunner.inTransaction(() -> {
            RecurringTransaction rule = ruleRepo.findById(ruleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Rule not found: " + ruleId));
            if (rule.getUserId() != userId) {
                throw new SecurityException("Access denied: rule does not belong to current user");
            }
            ruleRepo.deleteById(ruleId);
        });
    }
}
