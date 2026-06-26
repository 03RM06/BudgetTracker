package com.budgettracker.service;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.BudgetEnvelope;
import com.budgettracker.domain.Frequency;
import com.budgettracker.domain.RecurringTransaction;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.persistence.AccountRepository;
import com.budgettracker.persistence.BudgetEnvelopeRepository;
import com.budgettracker.persistence.RecurringTransactionRepository;
import com.budgettracker.persistence.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RecurrenceServiceTest {

    // ── throw-all stubs for deps that must never be called ────────────────────

    private static class ThrowAccountRepo implements AccountRepository {
        @Override public Account save(Account a) { throw new UnsupportedOperationException(); }
        @Override public Optional<Account> findById(long id) { throw new UnsupportedOperationException(); }
        @Override public List<Account> findByUserId(long u) { throw new UnsupportedOperationException(); }
        @Override public List<Account> findActiveByUserId(long u) { throw new UnsupportedOperationException(); }
        @Override public void update(Account a) { throw new UnsupportedOperationException(); }
        @Override public void updateBalance(long id, BigDecimal b) { throw new UnsupportedOperationException(); }
        @Override public void deleteById(long id) { throw new UnsupportedOperationException(); }
    }

    private static class ThrowTxRepo implements TransactionRepository {
        @Override public Transaction save(Transaction t) { throw new UnsupportedOperationException(); }
        @Override public Optional<Transaction> findById(long id) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByUserId(long u) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByAccountId(long a) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByUserIdAndDateRange(long u, LocalDate f, LocalDate t) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByUserIdCategoryAndDateRange(long u, long c, LocalDate f, LocalDate t) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByRecurringId(long r) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findTransferCreditsForAccount(long a) { throw new UnsupportedOperationException(); }
        @Override public void update(Transaction t) { throw new UnsupportedOperationException(); }
        @Override public void deleteById(long id) { throw new UnsupportedOperationException(); }
    }

    private static class ThrowEnvelopeRepo implements BudgetEnvelopeRepository {
        @Override public BudgetEnvelope save(BudgetEnvelope e) { throw new UnsupportedOperationException(); }
        @Override public Optional<BudgetEnvelope> findById(long id) { throw new UnsupportedOperationException(); }
        @Override public List<BudgetEnvelope> findByUserId(long u) { throw new UnsupportedOperationException(); }
        @Override public List<BudgetEnvelope> findActiveByUserIdAndDate(long u, LocalDate d) { throw new UnsupportedOperationException(); }
        @Override public Optional<BudgetEnvelope> findByCategoryAndPeriodStart(long u, long c, LocalDate p) { throw new UnsupportedOperationException(); }
        @Override public void update(BudgetEnvelope e) { throw new UnsupportedOperationException(); }
        @Override public void deleteById(long id) { throw new UnsupportedOperationException(); }
    }

    private static class ThrowRecurringRepo implements RecurringTransactionRepository {
        @Override public RecurringTransaction save(RecurringTransaction r) { throw new UnsupportedOperationException(); }
        @Override public Optional<RecurringTransaction> findById(long id) { throw new UnsupportedOperationException(); }
        @Override public List<RecurringTransaction> findByUserId(long u) { throw new UnsupportedOperationException(); }
        @Override public List<RecurringTransaction> findDueByDate(LocalDate d) { throw new UnsupportedOperationException(); }
        @Override public void update(RecurringTransaction r) { throw new UnsupportedOperationException(); }
        @Override public void updateNextRunDate(long id, LocalDate d) { throw new UnsupportedOperationException(); }
        @Override public void deleteById(long id) { throw new UnsupportedOperationException(); }
    }

    // ── stubs for idempotency test ─────────────────────────────────────────────

    private static class StubRecurringRepo extends ThrowRecurringRepo {
        final List<RecurringTransaction> dueRules = new ArrayList<>();

        @Override public List<RecurringTransaction> findDueByDate(LocalDate d) { return dueRules; }
        @Override public void updateNextRunDate(long id, LocalDate d) {}
    }

    private static class StubTxRepoForRecurrence extends ThrowTxRepo {
        final Map<Long, List<Transaction>> byRecurringId = new HashMap<>();

        @Override public List<Transaction> findByRecurringId(long recurringId) {
            return byRecurringId.getOrDefault(recurringId, List.of());
        }
    }

    private static class CapturingTxService extends TransactionService {
        final List<Transaction> added = new ArrayList<>();

        CapturingTxService() {
            super(new ThrowTxRepo(),
                    new AccountService(new ThrowAccountRepo(), new ThrowTxRepo()),
                    new BudgetService(new ThrowEnvelopeRepo(), new ThrowTxRepo()));
        }

        @Override
        public Transaction addTransaction(Transaction tx) {
            added.add(tx);
            return tx;
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private RecurrenceService serviceWithNoopDeps() {
        return new RecurrenceService(new ThrowRecurringRepo(), new ThrowTxRepo(),
                new TransactionService(new ThrowTxRepo(),
                        new AccountService(new ThrowAccountRepo(), new ThrowTxRepo()),
                        new BudgetService(new ThrowEnvelopeRepo(), new ThrowTxRepo())));
    }

    private RecurringTransaction rule(Frequency freq, int interval) {
        RecurringTransaction r = new RecurringTransaction();
        r.setId(1L);
        r.setUserId(1L);
        r.setAccountId(10L);
        r.setDirection(TxDirection.EXPENSE);
        r.setTemplateAmount(new BigDecimal("500.0000"));
        r.setCurrency("PHP");
        r.setFrequency(freq);
        r.setIntervalCount(interval);
        r.setAnchorDate(LocalDate.of(2026, 1, 15));
        r.setZoneId("Asia/Manila");
        r.setActive(true);
        r.setCreatedAt(Instant.now());
        r.setUpdatedAt(Instant.now());
        return r;
    }

    // ── advanceDate: all frequency values ─────────────────────────────────────

    @Test
    void advanceDate_daily() {
        LocalDate from = LocalDate.of(2026, 1, 15);
        assertEquals(LocalDate.of(2026, 1, 16),
                serviceWithNoopDeps().advanceDate(rule(Frequency.DAILY, 1), from));
    }

    @Test
    void advanceDate_weekly() {
        LocalDate from = LocalDate.of(2026, 1, 15);
        assertEquals(LocalDate.of(2026, 1, 22),
                serviceWithNoopDeps().advanceDate(rule(Frequency.WEEKLY, 1), from));
    }

    @Test
    void advanceDate_biweekly() {
        LocalDate from = LocalDate.of(2026, 1, 15);
        assertEquals(LocalDate.of(2026, 1, 29),
                serviceWithNoopDeps().advanceDate(rule(Frequency.BIWEEKLY, 1), from));
    }

    @Test
    void advanceDate_monthly() {
        LocalDate from = LocalDate.of(2026, 1, 15);
        assertEquals(LocalDate.of(2026, 2, 15),
                serviceWithNoopDeps().advanceDate(rule(Frequency.MONTHLY, 1), from));
    }

    @Test
    void advanceDate_quarterly() {
        LocalDate from = LocalDate.of(2026, 1, 15);
        assertEquals(LocalDate.of(2026, 4, 15),
                serviceWithNoopDeps().advanceDate(rule(Frequency.QUARTERLY, 1), from));
    }

    @Test
    void advanceDate_yearly() {
        LocalDate from = LocalDate.of(2026, 1, 15);
        assertEquals(LocalDate.of(2027, 1, 15),
                serviceWithNoopDeps().advanceDate(rule(Frequency.YEARLY, 1), from));
    }

    // ── advanceDate: dayOfMonth clamping ──────────────────────────────────────

    @Test
    void advanceDate_monthly_clampsDayOfMonthToMonthLength() {
        RecurringTransaction r = rule(Frequency.MONTHLY, 1);
        r.setDayOfMonth(31);
        LocalDate jan31 = LocalDate.of(2026, 1, 31);

        // Feb 2026 has 28 days; min(31, 28) = 28
        assertEquals(LocalDate.of(2026, 2, 28),
                serviceWithNoopDeps().advanceDate(r, jan31));
    }

    @Test
    void advanceDate_quarterly_clampsDayOfMonthToMonthLength() {
        RecurringTransaction r = rule(Frequency.QUARTERLY, 1);
        r.setDayOfMonth(31);
        LocalDate jan31 = LocalDate.of(2026, 1, 31);

        // Apr has 30 days; min(31, 30) = 30
        assertEquals(LocalDate.of(2026, 4, 30),
                serviceWithNoopDeps().advanceDate(r, jan31));
    }

    // ── materializeDue: idempotency ───────────────────────────────────────────

    @Test
    void materializeDue_doesNotRematerializeExistingTransaction() {
        LocalDate today = LocalDate.now();

        RecurringTransaction r = new RecurringTransaction();
        r.setId(1L);
        r.setUserId(1L);
        r.setAccountId(10L);
        r.setDirection(TxDirection.EXPENSE);
        r.setTemplateAmount(new BigDecimal("500.0000"));
        r.setCurrency("PHP");
        r.setFrequency(Frequency.DAILY);
        r.setIntervalCount(1);
        r.setAnchorDate(today);
        r.setNextRunDate(today);
        r.setZoneId("Asia/Manila");
        r.setActive(true);
        r.setCreatedAt(Instant.now());
        r.setUpdatedAt(Instant.now());

        // Existing transaction for today — same account as the rule
        Transaction existing = new Transaction();
        existing.setId(99L);
        existing.setAccountId(10L);
        existing.setRecurringId(1L);
        existing.setOccurredOn(today);
        existing.setDirection(TxDirection.EXPENSE);
        existing.setAmount(new BigDecimal("500.0000"));
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        StubRecurringRepo recurringRepo = new StubRecurringRepo();
        recurringRepo.dueRules.add(r);

        StubTxRepoForRecurrence txRepo = new StubTxRepoForRecurrence();
        txRepo.byRecurringId.put(1L, List.of(existing));

        CapturingTxService capturingTxService = new CapturingTxService();
        RecurrenceService svc = new RecurrenceService(recurringRepo, txRepo, capturingTxService);

        svc.materializeDue(1L);

        assertEquals(0, capturingTxService.added.size(),
                "addTransaction must not be called for a (recurringId, occurredOn) pair already materialized");
    }
}
