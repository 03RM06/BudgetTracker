package com.budgettracker.service;

import com.budgettracker.domain.BudgetTrackerException;
import com.budgettracker.domain.InvalidTransactionException;
import com.budgettracker.domain.ResourceNotFoundException;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class TransactionService {

    private final TransactionRepository txRepo;
    private final AccountService accountService;
    private final BudgetService budgetService;

    public TransactionService(TransactionRepository txRepo,
                              AccountService accountService,
                              BudgetService budgetService) {
        this.txRepo = txRepo;
        this.accountService = accountService;
        this.budgetService = budgetService;
    }

    public Transaction addTransaction(Transaction tx) {
        if (tx.getAmount() == null || tx.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Transaction amount must be > 0");
        }
        if (tx.getDirection() == TxDirection.INCOME && tx.getIncomeNature() == null) {
            throw new InvalidTransactionException(
                    "Income nature must not be null for INCOME transactions");
        }
        if (tx.getDirection() == TxDirection.TRANSFER) {
            if (tx.getTransferAccountId() == null) {
                throw new InvalidTransactionException(
                        "Transfer account ID must not be null for TRANSFER transactions");
            }
            if (tx.getTransferAccountId() == tx.getAccountId()) {
                throw new InvalidTransactionException(
                        "Transfer account ID must differ from source account ID");
            }
        }
        if (tx.getCreatedAt() == null) {
            Instant now = Instant.now();
            tx.setCreatedAt(now);
            tx.setUpdatedAt(now);
        }
        try {
            Transaction saved = txRepo.save(tx);
            accountService.recomputeBalance(tx.getAccountId());
            if (tx.getDirection() == TxDirection.TRANSFER) {
                accountService.recomputeBalance(tx.getTransferAccountId());
            }
            if (tx.getDirection() == TxDirection.EXPENSE && tx.getCategoryId() != null) {
                budgetService.evaluateEnvelopes(tx.getUserId(), tx.getCategoryId(), tx.getOccurredOn());
            }
            return saved;
        } catch (InvalidTransactionException e) {
            throw e;
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to save transaction", e);
        }
    }

    public void deleteTransaction(long txId) {
        try {
            Transaction tx = txRepo.findById(txId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Transaction not found: " + txId));
            long accountId = tx.getAccountId();
            Long transferAccountId = tx.getTransferAccountId();
            txRepo.deleteById(txId);
            accountService.recomputeBalance(accountId);
            if (tx.getDirection() == TxDirection.TRANSFER && transferAccountId != null) {
                accountService.recomputeBalance(transferAccountId);
            }
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to delete transaction", e);
        }
    }

    public List<Transaction> getTransactionsByDateRange(long userId, LocalDate from, LocalDate to) {
        try {
            return txRepo.findByUserIdAndDateRange(userId, from, to);
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to fetch transactions", e);
        }
    }

    public List<Transaction> getTransactionsByAccount(long accountId) {
        try {
            return txRepo.findByAccountId(accountId);
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to fetch transactions", e);
        }
    }

}
