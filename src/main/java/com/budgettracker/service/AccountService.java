package com.budgettracker.service;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.AccountType;
import com.budgettracker.domain.BudgetTrackerException;
import com.budgettracker.domain.DuplicateResourceException;
import com.budgettracker.domain.ResourceNotFoundException;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxStatus;
import com.budgettracker.persistence.AccountRepository;
import com.budgettracker.persistence.DataAccessException;
import com.budgettracker.persistence.TransactionRepository;
import com.budgettracker.persistence.TxRunner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final TxRunner txRunner;
    private final AccountRepository accountRepo;
    private final TransactionRepository txRepo;

    public AccountService(TxRunner txRunner, AccountRepository accountRepo, TransactionRepository txRepo) {
        this.txRunner = txRunner;
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
        } catch (DataAccessException e) {
            log.error("Failed to create account", e);
            throw new BudgetTrackerException("Failed to create account", e);
        }
    }

    public List<Account> getActiveAccounts(long userId) {
        try {
            return accountRepo.findActiveByUserId(userId);
        } catch (DataAccessException e) {
            log.error("Failed to fetch accounts for userId={}", userId, e);
            throw new BudgetTrackerException("Failed to fetch accounts", e);
        }
    }

    public Account getById(long accountId) {
        try {
            return accountRepo.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to find account id={}", accountId, e);
            throw new BudgetTrackerException("Failed to find account", e);
        }
    }

    public void recomputeBalance(long accountId) {
        try {
            Account account = accountRepo.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
            BigDecimal balance = account.getOpeningBalance();

            // Debit side: POSTED transactions where this account is the primary account
            for (Transaction tx : txRepo.findByAccountId(accountId)) {
                if (tx.getStatus() != TxStatus.POSTED) continue;
                switch (tx.getDirection()) {
                    case INCOME    -> balance = balance.add(tx.getAmount());
                    case EXPENSE   -> balance = balance.subtract(tx.getAmount());
                    case TRANSFER  -> balance = balance.subtract(tx.getAmount()); // sending money out
                }
            }

            // Credit side: POSTED transfer transactions where this account is the destination
            for (Transaction tx : txRepo.findTransferCreditsForAccount(accountId)) {
                if (tx.getStatus() != TxStatus.POSTED) continue;
                BigDecimal credit;
                // Apply exchange rate when source and destination currencies differ
                if (tx.getExchangeRate() != null
                        && tx.getCurrency() != null
                        && !tx.getCurrency().equals(account.getCurrency())) {
                    credit = tx.getAmount()
                            .multiply(tx.getExchangeRate())
                            .setScale(4, RoundingMode.HALF_EVEN);
                } else {
                    credit = tx.getAmount();
                }
                balance = balance.add(credit);
            }

            accountRepo.updateBalance(accountId, balance);
        } catch (DataAccessException e) {
            log.error("Failed to recompute balance for account id={}", accountId, e);
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
        } catch (DataAccessException e) {
            log.error("Failed to archive account id={}", accountId, e);
            throw new BudgetTrackerException("Failed to archive account", e);
        }
    }

    /** H3: ownership-checking variant used by the UI layer. */
    public void archiveAccount(long accountId, long userId) {
        try {
            Account account = accountRepo.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
            if (account.getUserId() != userId) {
                throw new SecurityException("Access denied: account does not belong to current user");
            }
            account.setArchived(true);
            account.setUpdatedAt(Instant.now());
            accountRepo.update(account);
        } catch (ResourceNotFoundException | SecurityException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to archive account id={}", accountId, e);
            throw new BudgetTrackerException("Failed to archive account", e);
        }
    }
}
