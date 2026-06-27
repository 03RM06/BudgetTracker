package com.budgettracker.service;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.AccountType;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.domain.TxStatus;
import com.budgettracker.persistence.AccountRepository;
import com.budgettracker.persistence.DirectTxRunner;
import com.budgettracker.persistence.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C3 — cross-currency credit in AccountService.recomputeBalance:
 *   - Same-currency transfers: exchange rate is ignored; credit = raw amount.
 *   - Cross-currency transfers: credit = amount × exchangeRate, HALF_EVEN, 4 dp.
 *   - The HALF_EVEN rounding is verified with a value where HALF_UP would differ.
 */
class AccountServiceCurrencyTest {

    // ── stubs (pattern from AccountServiceTest) ────────────────────────────────

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

        @Override public void updateBalance(long id, BigDecimal bal) {
            capturedBalance = bal;
        }

        @Override public void deleteById(long id) {}
    }

    private static class StubTxRepo implements TransactionRepository {
        final List<Transaction> byAccount      = new ArrayList<>();
        final List<Transaction> creditTransfers = new ArrayList<>();

        @Override public Transaction save(Transaction t) { return t; }

        @Override public Optional<Transaction> findById(long id) {
            throw new UnsupportedOperationException();
        }

        @Override public List<Transaction> findByUserId(long u) {
            throw new UnsupportedOperationException();
        }

        @Override public List<Transaction> findByAccountId(long accountId) {
            return byAccount.stream()
                    .filter(t -> t.getAccountId() == accountId)
                    .collect(Collectors.toList());
        }

        @Override public List<Transaction> findByUserIdAndDateRange(long u, LocalDate f, LocalDate t) {
            throw new UnsupportedOperationException();
        }

        @Override public List<Transaction> findByUserIdCategoryAndDateRange(long u, long c, LocalDate f, LocalDate t) {
            throw new UnsupportedOperationException();
        }

        @Override public List<Transaction> findByRecurringId(long r) {
            throw new UnsupportedOperationException();
        }

        @Override public List<Transaction> findTransferCreditsForAccount(long accountId) {
            return creditTransfers.stream()
                    .filter(t -> t.getTransferAccountId() != null
                            && t.getTransferAccountId() == accountId)
                    .collect(Collectors.toList());
        }

        @Override public void update(Transaction t) {}
        @Override public void deleteById(long id) {}
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Account account(long id, long userId, String currency, BigDecimal opening) {
        Account a = new Account();
        a.setId(id);
        a.setUserId(userId);
        a.setName("Account-" + id);
        a.setType(AccountType.BANK);
        a.setOpeningBalance(opening);
        a.setCurrentBalance(opening);
        a.setCurrency(currency);
        a.setArchived(false);
        a.setCreatedAt(Instant.now());
        a.setUpdatedAt(Instant.now());
        return a;
    }

    /**
     * Builds a POSTED TRANSFER transaction as seen by the destination account's credit scan.
     * accountId = source, transferAccountId = destination.
     */
    private Transaction transferCredit(long sourceAccountId, long destAccountId,
                                       String sourceCurrency,
                                       BigDecimal amount, BigDecimal exchangeRate) {
        Transaction t = new Transaction();
        t.setAccountId(sourceAccountId);
        t.setTransferAccountId(destAccountId);
        t.setDirection(TxDirection.TRANSFER);
        t.setStatus(TxStatus.POSTED);
        t.setAmount(amount);
        t.setCurrency(sourceCurrency);
        t.setExchangeRate(exchangeRate);
        t.setOccurredOn(LocalDate.of(2026, 6, 1));
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return t;
    }

    // ── same-currency transfer: exchange rate must NOT be applied ─────────────

    @Test
    void recomputeBalance_sameCurrencyTransfer_creditsRawAmountIgnoringExchangeRate() {
        // Destination account: PHP, opening = 500
        StubAccountRepo accountRepo = new StubAccountRepo();
        accountRepo.store.add(account(20L, 1L, "PHP", new BigDecimal("500.0000")));

        StubTxRepo txRepo = new StubTxRepo();
        // Exchange rate is 2.0 — if applied by mistake, balance would be 500 + 500*2.0 = 1500.
        // Correct (same currency): balance = 500 + 500 = 1000.
        txRepo.creditTransfers.add(
                transferCredit(10L, 20L, "PHP", new BigDecimal("500.0000"), new BigDecimal("2.0")));

        new AccountService(new DirectTxRunner(), accountRepo, txRepo).recomputeBalance(20L);

        assertEquals(0, accountRepo.capturedBalance.compareTo(new BigDecimal("1000.0000")),
                "Same-currency credit must use raw amount (500), not amount × rate (1000)");
    }

    // ── cross-currency transfer: credit = amount × rate, HALF_EVEN, 4 dp ─────

    @Test
    void recomputeBalance_crossCurrencyTransfer_creditsAmountTimesExchangeRateHalfEven() {
        // Destination account: PHP, opening = 0
        StubAccountRepo accountRepo = new StubAccountRepo();
        accountRepo.store.add(account(20L, 1L, "PHP", new BigDecimal("0.0000")));

        StubTxRepo txRepo = new StubTxRepo();
        // Source: USD 100.00, rate = 56.789
        // Credit = 100.00 × 56.789 = 5678.9000 (exactly 4 dp, no rounding needed here)
        txRepo.creditTransfers.add(
                transferCredit(10L, 20L, "USD", new BigDecimal("100.00"), new BigDecimal("56.789")));

        new AccountService(new DirectTxRunner(), accountRepo, txRepo).recomputeBalance(20L);

        assertEquals(0, accountRepo.capturedBalance.compareTo(new BigDecimal("5678.9000")),
                "Cross-currency credit must be amount × exchangeRate scaled to 4 dp");
    }

    /**
     * Verifies the rounding mode is specifically HALF_EVEN (not HALF_UP).
     *
     * 1.00 USD × 56.789250 = 56.78925000 (scale 8 after multiply)
     * setScale(4, HALF_EVEN): 4th decimal is 2 (even) → stays at 56.7892
     * setScale(4, HALF_UP) : 5th decimal is 5        → rounds up to 56.7893
     */
    @Test
    void recomputeBalance_crossCurrencyTransfer_usesHalfEvenNotHalfUp() {
        StubAccountRepo accountRepo = new StubAccountRepo();
        accountRepo.store.add(account(20L, 1L, "PHP", new BigDecimal("0.0000")));

        StubTxRepo txRepo = new StubTxRepo();
        txRepo.creditTransfers.add(
                transferCredit(10L, 20L, "USD",
                        new BigDecimal("1.00"),
                        new BigDecimal("56.789250")));

        new AccountService(new DirectTxRunner(), accountRepo, txRepo).recomputeBalance(20L);

        // HALF_EVEN: 56.78925 → 56.7892  (4th decimal = 2, even, do not round up)
        // HALF_UP  : 56.78925 → 56.7893  (5th decimal = 5, always round up)
        assertEquals(0, accountRepo.capturedBalance.compareTo(new BigDecimal("56.7892")),
                "recomputeBalance must use HALF_EVEN rounding, not HALF_UP");
    }

    // ── cross-currency: null exchangeRate falls back to raw amount ────────────

    @Test
    void recomputeBalance_crossCurrencyTransfer_nullExchangeRateFallsBackToRawAmount() {
        StubAccountRepo accountRepo = new StubAccountRepo();
        accountRepo.store.add(account(20L, 1L, "PHP", new BigDecimal("0.0000")));

        StubTxRepo txRepo = new StubTxRepo();
        // Different currencies but no exchange rate provided → credit = raw amount
        Transaction tx = transferCredit(10L, 20L, "USD", new BigDecimal("200.00"), null);
        txRepo.creditTransfers.add(tx);

        new AccountService(new DirectTxRunner(), accountRepo, txRepo).recomputeBalance(20L);

        assertEquals(0, accountRepo.capturedBalance.compareTo(new BigDecimal("200.00")),
                "When exchangeRate is null, credit must fall back to the raw amount");
    }
}
