package com.budgettracker.service;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.AccountType;
import com.budgettracker.domain.BudgetTrackerException;
import com.budgettracker.domain.DuplicateResourceException;
import com.budgettracker.domain.ResourceNotFoundException;
import com.budgettracker.domain.Transaction;
import com.budgettracker.persistence.AccountRepository;
import com.budgettracker.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public class AccountService {

    private final AccountRepository accountRepo;
    private final TransactionRepository txRepo;

    public AccountService(AccountRepository accountRepo, TransactionRepository txRepo) {
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;
    }

    public Account createAccount(long userId, String name, AccountType type,
                                 BigDecimal openingBalance, String currency) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Account name must not be blank");
        }
        if (openingBalance == null || openingBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Opening balance must not be null and must be >= 0");
        }
        try {
            boolean nameTaken = accountRepo.findByUserId(userId).stream()
                    .filter(a -> !a.isArchived())
                    .anyMatch(a -> a.getName().equalsIgnoreCase(name));
            if (nameTaken) {
                throw new DuplicateResourceException(
                        "An active account with that name already exists for this user: " + name);
            }
            Account account = new Account();
            account.setUserId(userId);
            account.setName(name);
            account.setType(type);
            account.setOpeningBalance(openingBalance);
            account.setCurrentBalance(openingBalance);
            account.setCurrency(currency);
            account.setArchived(false);
            Instant now = Instant.now();
            account.setCreatedAt(now);
            account.setUpdatedAt(now);
            return accountRepo.save(account);
        } catch (DuplicateResourceException e) {
            throw e;
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to create account", e);
        }
    }

    public List<Account> getActiveAccounts(long userId) {
        try {
            return accountRepo.findActiveByUserId(userId);
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to fetch accounts", e);
        }
    }

    public Account getById(long accountId) {
        try {
            return accountRepo.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to find account", e);
        }
    }

    public void recomputeBalance(long accountId) {
        try {
            Account account = accountRepo.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
            BigDecimal balance = account.getOpeningBalance();

            // Debit side: transactions where this account is the primary account
            for (Transaction tx : txRepo.findByAccountId(accountId)) {
                switch (tx.getDirection()) {
                    case INCOME    -> balance = balance.add(tx.getAmount());
                    case EXPENSE   -> balance = balance.subtract(tx.getAmount());
                    case TRANSFER  -> balance = balance.subtract(tx.getAmount()); // sending money out
                }
            }

            // Credit side: transfer transactions where this account is the destination
            for (Transaction tx : txRepo.findTransferCreditsForAccount(accountId)) {
                balance = balance.add(tx.getAmount());
            }

            accountRepo.updateBalance(accountId, balance);
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to recompute balance for account " + accountId, e);
        }
    }

    public void archiveAccount(long accountId) {
        try {
            Account account = accountRepo.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
            account.setArchived(true);
            account.setUpdatedAt(Instant.now());
            accountRepo.update(account);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (SQLException e) {
            throw new BudgetTrackerException("Failed to archive account", e);
        }
    }
}
