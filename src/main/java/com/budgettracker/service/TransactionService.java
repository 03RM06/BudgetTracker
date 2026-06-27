package com.budgettracker.service;

import com.budgettracker.domain.InvalidTransactionException;
import com.budgettracker.domain.ResourceNotFoundException;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.domain.TxStatus;
import com.budgettracker.persistence.TransactionRepository;
import com.budgettracker.persistence.TxRunner;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TxRunner txRunner;
    private final TransactionRepository txRepo;
    private final AccountService accountService;
    private final BudgetService budgetService;

    public TransactionService(TxRunner txRunner,
                              TransactionRepository txRepo,
                              AccountService accountService,
                              BudgetService budgetService) {
        this.txRunner = txRunner;
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
        return txRunner.inTransaction(() -> {
            Transaction saved = txRepo.save(tx);
            if (tx.getStatus() == TxStatus.POSTED) {
                accountService.recomputeBalance(tx.getAccountId());
                if (tx.getDirection() == TxDirection.TRANSFER && tx.getTransferAccountId() != null) {
                    accountService.recomputeBalance(tx.getTransferAccountId());
                }
                if (tx.getDirection() == TxDirection.EXPENSE && tx.getCategoryId() != null) {
                    budgetService.evaluateEnvelopes(tx.getUserId(), tx.getCategoryId(), tx.getOccurredOn());
                }
            }
            return saved;
        });
    }

    public void deleteTransaction(long txId) {
        txRunner.inTransaction(() -> {
            Transaction tx = txRepo.findById(txId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Transaction not found: " + txId));
            txRepo.deleteById(txId);
            accountService.recomputeBalance(tx.getAccountId());
            if (tx.getDirection() == TxDirection.TRANSFER && tx.getTransferAccountId() != null) {
                accountService.recomputeBalance(tx.getTransferAccountId());
            }
            if (tx.getDirection() == TxDirection.EXPENSE && tx.getCategoryId() != null) {
                budgetService.evaluateEnvelopes(tx.getUserId(), tx.getCategoryId(), tx.getOccurredOn());
            }
        });
    }

    /** H3: ownership-checking variant used by the UI layer. */
    public void deleteTransaction(long txId, long userId) {
        txRunner.inTransaction(() -> {
            Transaction tx = txRepo.findById(txId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Transaction not found: " + txId));
            if (tx.getUserId() != userId) {
                throw new SecurityException("Access denied: transaction does not belong to current user");
            }
            txRepo.deleteById(txId);
            accountService.recomputeBalance(tx.getAccountId());
            if (tx.getDirection() == TxDirection.TRANSFER && tx.getTransferAccountId() != null) {
                accountService.recomputeBalance(tx.getTransferAccountId());
            }
            if (tx.getDirection() == TxDirection.EXPENSE && tx.getCategoryId() != null) {
                budgetService.evaluateEnvelopes(tx.getUserId(), tx.getCategoryId(), tx.getOccurredOn());
            }
        });
    }

    public Transaction updateTransaction(Transaction updated) {
        if (updated.getAmount() == null || updated.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Transaction amount must be > 0");
        }
        if (updated.getDirection() == TxDirection.INCOME && updated.getIncomeNature() == null) {
            throw new InvalidTransactionException(
                    "Income nature must not be null for INCOME transactions");
        }
        if (updated.getDirection() == TxDirection.TRANSFER) {
            if (updated.getTransferAccountId() == null) {
                throw new InvalidTransactionException(
                        "Transfer account ID must not be null for TRANSFER transactions");
            }
            if (updated.getTransferAccountId() == updated.getAccountId()) {
                throw new InvalidTransactionException(
                        "Transfer account ID must differ from source account ID");
            }
        }
        return txRunner.inTransaction(() -> {
            Transaction old = txRepo.findById(updated.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Transaction not found: " + updated.getId()));
            // H3: ownership check — the stored record must belong to the caller
            if (old.getUserId() != updated.getUserId()) {
                throw new SecurityException("Access denied: transaction does not belong to current user");
            }
            txRepo.update(updated);

            // Recompute all affected accounts (deduplicated)
            Set<Long> accounts = new HashSet<>();
            accounts.add(old.getAccountId());
            accounts.add(updated.getAccountId());
            if (old.getDirection() == TxDirection.TRANSFER && old.getTransferAccountId() != null) {
                accounts.add(old.getTransferAccountId());
            }
            if (updated.getDirection() == TxDirection.TRANSFER && updated.getTransferAccountId() != null) {
                accounts.add(updated.getTransferAccountId());
            }
            for (Long a : accounts) {
                accountService.recomputeBalance(a);
            }

            // Re-evaluate old and new expense category envelopes
            if (old.getDirection() == TxDirection.EXPENSE && old.getCategoryId() != null) {
                budgetService.evaluateEnvelopes(old.getUserId(), old.getCategoryId(), old.getOccurredOn());
            }
            if (updated.getDirection() == TxDirection.EXPENSE && updated.getCategoryId() != null) {
                budgetService.evaluateEnvelopes(updated.getUserId(), updated.getCategoryId(), updated.getOccurredOn());
            }

            return updated;
        });
    }

    public List<Transaction> getTransactionsByDateRange(long userId, LocalDate from, LocalDate to) {
        return txRunner.inTransaction(() -> txRepo.findByUserIdAndDateRange(userId, from, to));
    }

    public List<Transaction> getTransactionsByAccount(long accountId) {
        return txRunner.inTransaction(() -> txRepo.findByAccountId(accountId));
    }

    public List<Transaction> getPendingTransactions(long userId) {
        return txRunner.inTransaction(() -> txRepo.findByUserIdAndStatus(userId, TxStatus.PENDING));
    }

    public Transaction confirmPending(long txId, BigDecimal actualAmount) {
        if (actualAmount == null || actualAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Actual amount must be positive");
        }
        return txRunner.inTransaction(() -> {
            Transaction tx = txRepo.findById(txId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txId));
            if (tx.getStatus() != TxStatus.PENDING) {
                throw new InvalidTransactionException("Transaction is not PENDING");
            }
            tx.setAmount(actualAmount);
            tx.setStatus(TxStatus.POSTED);
            txRepo.update(tx);
            accountService.recomputeBalance(tx.getAccountId());
            if (tx.getDirection() == TxDirection.EXPENSE && tx.getCategoryId() != null) {
                budgetService.evaluateEnvelopes(tx.getUserId(), tx.getCategoryId(), tx.getOccurredOn());
            }
            return tx;
        });
    }

    public void cancelPending(long txId) {
        txRunner.inTransaction(() -> {
            Transaction tx = txRepo.findById(txId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txId));
            if (tx.getStatus() != TxStatus.PENDING) {
                throw new InvalidTransactionException("Transaction is not PENDING");
            }
            tx.setStatus(TxStatus.CANCELLED);
            txRepo.update(tx);
        });
    }

    /** H3: ownership-checking variant for confirmPending used by the UI layer. */
    public Transaction confirmPending(long txId, BigDecimal actualAmount, long userId) {
        if (actualAmount == null || actualAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Actual amount must be positive");
        }
        return txRunner.inTransaction(() -> {
            Transaction tx = txRepo.findById(txId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txId));
            if (tx.getUserId() != userId) {
                throw new SecurityException("Access denied: transaction does not belong to current user");
            }
            if (tx.getStatus() != TxStatus.PENDING) {
                throw new InvalidTransactionException("Transaction is not PENDING");
            }
            tx.setAmount(actualAmount);
            tx.setStatus(TxStatus.POSTED);
            txRepo.update(tx);
            accountService.recomputeBalance(tx.getAccountId());
            if (tx.getDirection() == TxDirection.EXPENSE && tx.getCategoryId() != null) {
                budgetService.evaluateEnvelopes(tx.getUserId(), tx.getCategoryId(), tx.getOccurredOn());
            }
            return tx;
        });
    }

    /** H3: ownership-checking variant for cancelPending used by the UI layer. */
    public void cancelPending(long txId, long userId) {
        txRunner.inTransaction(() -> {
            Transaction tx = txRepo.findById(txId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + txId));
            if (tx.getUserId() != userId) {
                throw new SecurityException("Access denied: transaction does not belong to current user");
            }
            if (tx.getStatus() != TxStatus.PENDING) {
                throw new InvalidTransactionException("Transaction is not PENDING");
            }
            tx.setStatus(TxStatus.CANCELLED);
            txRepo.update(tx);
        });
    }

    public List<Transaction> getAllTransactions(long userId) {
        return txRunner.inTransaction(() -> txRepo.findByUserId(userId));
    }
}
