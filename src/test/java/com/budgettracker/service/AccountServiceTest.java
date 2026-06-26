package com.budgettracker.service;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.AccountType;
import com.budgettracker.domain.DuplicateResourceException;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.persistence.AccountRepository;
import com.budgettracker.persistence.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {

    // ── stubs ─────────────────────────────────────────────────────────────────

    private static class StubAccountRepo implements AccountRepository {
        final List<Account> store = new ArrayList<>();
        BigDecimal capturedBalance;

        @Override public Account save(Account a) { store.add(a); return a; }
        @Override public Optional<Account> findById(long id) {
            return store.stream().filter(a -> a.getId() == id).findFirst();
        }
        @Override public List<Account> findByUserId(long userId) {
            return store.stream().filter(a -> a.getUserId() == userId).collect(Collectors.toList());
        }
        @Override public List<Account> findActiveByUserId(long userId) {
            return findByUserId(userId).stream().filter(a -> !a.isArchived()).collect(Collectors.toList());
        }
        @Override public void update(Account a) {}
        @Override public void updateBalance(long id, BigDecimal bal) { capturedBalance = bal; }
        @Override public void deleteById(long id) {}
    }

    private static class StubTxRepo implements TransactionRepository {
        final List<Transaction> byAccount = new ArrayList<>();
        final List<Transaction> creditTransfers = new ArrayList<>();

        @Override public Transaction save(Transaction t) { return t; }
        @Override public Optional<Transaction> findById(long id) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByUserId(long u) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByAccountId(long accountId) {
            return byAccount.stream().filter(t -> t.getAccountId() == accountId).collect(Collectors.toList());
        }
        @Override public List<Transaction> findByUserIdAndDateRange(long u, LocalDate f, LocalDate t) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByUserIdCategoryAndDateRange(long u, long c, LocalDate f, LocalDate t) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByRecurringId(long r) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findTransferCreditsForAccount(long accountId) {
            return creditTransfers.stream()
                    .filter(t -> t.getTransferAccountId() != null
                            && t.getTransferAccountId().longValue() == accountId)
                    .collect(Collectors.toList());
        }
        @Override public void update(Transaction t) {}
        @Override public void deleteById(long id) {}
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Account account(long id, long userId, BigDecimal opening) {
        Account a = new Account();
        a.setId(id);
        a.setUserId(userId);
        a.setName("Account-" + id);
        a.setType(AccountType.BANK);
        a.setOpeningBalance(opening);
        a.setCurrentBalance(opening);
        a.setCurrency("PHP");
        a.setArchived(false);
        a.setCreatedAt(Instant.now());
        a.setUpdatedAt(Instant.now());
        return a;
    }

    private Transaction transferTx(long sourceId, long destId, BigDecimal amount) {
        Transaction t = new Transaction();
        t.setAccountId(sourceId);
        t.setTransferAccountId(destId);
        t.setDirection(TxDirection.TRANSFER);
        t.setAmount(amount);
        t.setOccurredOn(LocalDate.of(2026, 6, 1));
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return t;
    }

    // ── recomputeBalance: single-row transfer model ───────────────────────────

    @Test
    void recomputeBalance_sourceAccountIsDebited() throws SQLException {
        StubAccountRepo accountRepo = new StubAccountRepo();
        StubTxRepo txRepo = new StubTxRepo();
        accountRepo.store.add(account(10L, 1L, new BigDecimal("1000.0000")));
        txRepo.byAccount.add(transferTx(10L, 20L, new BigDecimal("300.0000")));

        new AccountService(accountRepo, txRepo).recomputeBalance(10L);

        assertEquals(0, accountRepo.capturedBalance.compareTo(new BigDecimal("700.0000")),
                "Source balance = opening - transfer amount");
    }

    @Test
    void recomputeBalance_destinationAccountIsCredited() throws SQLException {
        StubAccountRepo accountRepo = new StubAccountRepo();
        StubTxRepo txRepo = new StubTxRepo();
        accountRepo.store.add(account(20L, 1L, new BigDecimal("500.0000")));
        txRepo.creditTransfers.add(transferTx(10L, 20L, new BigDecimal("300.0000")));

        new AccountService(accountRepo, txRepo).recomputeBalance(20L);

        assertEquals(0, accountRepo.capturedBalance.compareTo(new BigDecimal("800.0000")),
                "Destination balance = opening + credited transfer");
    }

    // ── createAccount validation ──────────────────────────────────────────────

    @Test
    void createAccount_blankNameThrows() {
        AccountService svc = new AccountService(new StubAccountRepo(), new StubTxRepo());
        assertThrows(IllegalArgumentException.class,
                () -> svc.createAccount(1L, "  ", AccountType.CASH, new BigDecimal("0"), "PHP"));
    }

    @Test
    void createAccount_nullOpeningBalanceThrows() {
        AccountService svc = new AccountService(new StubAccountRepo(), new StubTxRepo());
        assertThrows(IllegalArgumentException.class,
                () -> svc.createAccount(1L, "Wallet", AccountType.CASH, null, "PHP"));
    }

    @Test
    void createAccount_negativeOpeningBalanceThrows() {
        AccountService svc = new AccountService(new StubAccountRepo(), new StubTxRepo());
        assertThrows(IllegalArgumentException.class,
                () -> svc.createAccount(1L, "Wallet", AccountType.CASH, new BigDecimal("-1"), "PHP"));
    }

    @Test
    void createAccount_duplicateActiveNameThrows() throws SQLException {
        StubAccountRepo accountRepo = new StubAccountRepo();
        Account existing = account(1L, 1L, BigDecimal.ZERO);
        existing.setName("Savings");
        accountRepo.store.add(existing);

        AccountService svc = new AccountService(accountRepo, new StubTxRepo());
        assertThrows(DuplicateResourceException.class,
                () -> svc.createAccount(1L, "savings", AccountType.BANK, new BigDecimal("100"), "PHP"));
    }
}
