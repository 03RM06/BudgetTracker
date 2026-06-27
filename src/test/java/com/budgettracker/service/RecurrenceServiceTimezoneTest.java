package com.budgettracker.service;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.BudgetEnvelope;
import com.budgettracker.domain.Frequency;
import com.budgettracker.domain.RecurringTransaction;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.persistence.AccountRepository;
import com.budgettracker.persistence.BudgetEnvelopeRepository;
import com.budgettracker.persistence.DirectTxRunner;
import com.budgettracker.persistence.RecurringTransactionRepository;
import com.budgettracker.persistence.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies H1: RecurrenceService uses each rule's own IANA zone when computing
 * "today" for due-date comparison, not the JVM's default system zone.
 *
 * "Pacific/Kiritimati" (UTC+14) is the furthest-ahead timezone on Earth and
 * is reliably different from any server-side default, making it a strong probe.
 *
 * MONTHLY frequency is used so that exactly one occurrence falls in the window
 * (yesterday-in-zone vs today-in-zone), with the next advance landing a full
 * month in the future.
 */
class RecurrenceServiceTimezoneTest {

    private static final String ZONE = "Pacific/Kiritimati";

    // ── throw-all stubs ───────────────────────────────────────────────────────

    private static class ThrowAccountRepo implements AccountRepository {
        @Override public Account save(Account a) { throw new UnsupportedOperationException(); }
        @Override public Optional<Account> findById(long id) { throw new UnsupportedOperationException(); }
        @Override public List<Account> findByUserId(long u) { throw new UnsupportedOperationException(); }
        @Override public List<Account> findActiveByUserId(long u) { throw new UnsupportedOperationException(); }
        @Override public void update(Account a) { throw new UnsupportedOperationException(); }
        @Override public void updateBalance(long id, BigDecimal b) { throw new UnsupportedOperationException(); }
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

    private static class ThrowRecurringRepo implements RecurringTransactionRepository {
        @Override public RecurringTransaction save(RecurringTransaction r) { throw new UnsupportedOperationException(); }
        @Override public Optional<RecurringTransaction> findById(long id) { throw new UnsupportedOperationException(); }
        @Override public List<RecurringTransaction> findByUserId(long u) { throw new UnsupportedOperationException(); }
        @Override public List<RecurringTransaction> findDueByDate(LocalDate d) { throw new UnsupportedOperationException(); }
        @Override public void update(RecurringTransaction r) { throw new UnsupportedOperationException(); }
        @Override public void updateNextRunDate(long id, LocalDate d) { throw new UnsupportedOperationException(); }
        @Override public void deleteById(long id) { throw new UnsupportedOperationException(); }
    }

    // ── functional stubs ─────────────────────────────────────────────────────

    /**
     * Returns a fixed set of rules regardless of the query date; silently
     * absorbs nextRunDate updates so the service can advance the cursor freely.
     */
    private static class StubRecurringRepo extends ThrowRecurringRepo {
        final List<RecurringTransaction> rules = new ArrayList<>();

        @Override public List<RecurringTransaction> findDueByDate(LocalDate d) { return rules; }
        @Override public void updateNextRunDate(long id, LocalDate d) { /* no-op */ }
    }

    /**
     * Returns an empty list for findByRecurringId so no occurrence appears
     * pre-existing and idempotency skipping does not suppress materialization.
     */
    private static class StubTxRepo extends ThrowTxRepo {
        @Override public List<Transaction> findByRecurringId(long r) { return List.of(); }
    }

    /**
     * Records every call to addTransaction without touching any repo or DB.
     * The override is total — super() is NOT called — so validation in
     * TransactionService does not run and no repository is needed.
     */
    private static class CapturingTxService extends TransactionService {
        final List<Transaction> added = new ArrayList<>();

        CapturingTxService() {
            super(new DirectTxRunner(),
                    new ThrowTxRepo(),
                    new AccountService(new DirectTxRunner(), new ThrowAccountRepo(), new ThrowTxRepo()),
                    new BudgetService(new DirectTxRunner(), new ThrowEnvelopeRepo(), new ThrowTxRepo()));
        }

        @Override
        public Transaction addTransaction(Transaction tx) {
            added.add(tx);
            return tx;
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    /**
     * Builds a MONTHLY recurring-expense rule for userId=1 / accountId=10 in
     * Pacific/Kiritimati.  MONTHLY is chosen deliberately: the next advance
     * from {nextRunDate} lands a full month ahead, so only one occurrence ever
     * falls within the [nextRunDate, today] window regardless of exact clock
     * position — keeping the expected count at exactly 1.
     */
    private RecurringTransaction makeMonthlyRule(LocalDate nextRunDate) {
        RecurringTransaction r = new RecurringTransaction();
        r.setId(1L);
        r.setUserId(1L);
        r.setAccountId(10L);
        r.setDirection(TxDirection.EXPENSE);
        r.setTemplateAmount(new BigDecimal("100.0000"));
        r.setCurrency("PHP");
        r.setFrequency(Frequency.MONTHLY);
        r.setIntervalCount(1);
        r.setAnchorDate(nextRunDate);
        r.setNextRunDate(nextRunDate);
        r.setZoneId(ZONE);
        r.setActive(true);
        r.setCreatedAt(Instant.now());
        r.setUpdatedAt(Instant.now());
        return r;
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    /**
     * A rule whose nextRunDate is yesterday in Pacific/Kiritimati time must
     * produce exactly one materialized transaction.
     *
     * This confirms that the service computes today using the rule's own zone
     * (UTC+14) rather than the JVM default, so the due-date check resolves
     * correctly regardless of what timezone the server runs in.
     */
    @Test
    void materializeDue_nextRunDateYesterdayInKiritimati_materializesExactlyOneTransaction() {
        LocalDate yesterday = LocalDate.now(ZoneId.of(ZONE)).minusDays(1);

        StubRecurringRepo recurringRepo = new StubRecurringRepo();
        recurringRepo.rules.add(makeMonthlyRule(yesterday));

        CapturingTxService txService = new CapturingTxService();
        RecurrenceService svc = new RecurrenceService(new DirectTxRunner(), recurringRepo, new StubTxRepo(), txService);

        svc.materializeDue(1L);

        assertEquals(1, txService.added.size(),
                "Rule with nextRunDate = yesterday (Pacific/Kiritimati) must produce exactly 1 transaction");
    }

    /**
     * A rule whose nextRunDate is 5 days in the future in Pacific/Kiritimati
     * time must not produce any materialized transaction.
     *
     * This confirms the loop guard (!cursor.isAfter(today)) uses the
     * zone-specific today and correctly rejects a future date.
     */
    @Test
    void materializeDue_nextRunDateFiveDaysAheadInKiritimati_materializesNoTransactions() {
        LocalDate futurePlus5 = LocalDate.now(ZoneId.of(ZONE)).plusDays(5);

        StubRecurringRepo recurringRepo = new StubRecurringRepo();
        recurringRepo.rules.add(makeMonthlyRule(futurePlus5));

        CapturingTxService txService = new CapturingTxService();
        RecurrenceService svc = new RecurrenceService(new DirectTxRunner(), recurringRepo, new StubTxRepo(), txService);

        svc.materializeDue(1L);

        assertEquals(0, txService.added.size(),
                "Rule with nextRunDate = 5 days ahead (Pacific/Kiritimati) must produce 0 transactions");
    }
}
