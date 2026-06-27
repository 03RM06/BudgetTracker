package com.budgettracker.service;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.BudgetEnvelope;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.domain.TxStatus;
import com.budgettracker.persistence.AccountRepository;
import com.budgettracker.persistence.BudgetEnvelopeRepository;
import com.budgettracker.persistence.DirectTxRunner;
import com.budgettracker.persistence.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C1 — addTransaction atomicity contract:
 *   A) POSTED tx: persisted AND recomputeBalance + evaluateEnvelopes triggered.
 *   B) Exception inside the unit-of-work propagates; it is never swallowed.
 *   C) PENDING tx: persisted but recomputeBalance and evaluateEnvelopes are skipped.
 */
class TransactionAtomicityTest {

    // ── throw-all stubs ───────────────────────────────────────────────────────

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

    // ── recording / capturing stubs ───────────────────────────────────────────

    /** Records every call to save(); assigns sequential IDs. */
    private static class RecordingSaveTxRepo extends ThrowTxRepo {
        final List<Transaction> saved = new ArrayList<>();

        @Override
        public Transaction save(Transaction t) {
            t.setId(saved.size() + 1L);
            saved.add(t);
            return t;
        }
    }

    /**
     * Overrides recomputeBalance so tests can observe calls and optionally
     * inject a failure without touching the real AccountRepository.
     */
    private static class CapturingAccountService extends AccountService {
        final List<Long> recomputedAccounts = new ArrayList<>();
        RuntimeException throwOnRecompute = null;

        CapturingAccountService() {
            super(new DirectTxRunner(), new ThrowAccountRepo(), new ThrowTxRepo());
        }

        @Override
        public void recomputeBalance(long accountId) {
            recomputedAccounts.add(accountId);
            if (throwOnRecompute != null) throw throwOnRecompute;
        }
    }

    /** Overrides evaluateEnvelopes so tests can assert it was (or wasn't) called. */
    private static class CapturingBudgetService extends BudgetService {
        final List<Long> evaluatedCategoryIds = new ArrayList<>();

        CapturingBudgetService() {
            super(new DirectTxRunner(), new ThrowEnvelopeRepo(), new ThrowTxRepo());
        }

        @Override
        public List<EnvelopeStatus> evaluateEnvelopes(long userId, long categoryId, LocalDate date) {
            evaluatedCategoryIds.add(categoryId);
            return List.of();
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    /** A POSTED EXPENSE with a category — exercises both recompute and evaluate paths. */
    private Transaction postedExpenseTx() {
        Transaction t = new Transaction();
        t.setUserId(1L);
        t.setAccountId(10L);
        t.setDirection(TxDirection.EXPENSE);
        t.setStatus(TxStatus.POSTED);
        t.setAmount(new BigDecimal("250.00"));
        t.setCategoryId(5L);
        t.setCurrency("PHP");
        t.setOccurredOn(LocalDate.of(2026, 6, 15));
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return t;
    }

    // ── Scenario A: happy path ────────────────────────────────────────────────

    @Test
    void addTransaction_postedExpense_persistsTxAndTriggersRecomputeAndEvaluate() {
        RecordingSaveTxRepo txRepo         = new RecordingSaveTxRepo();
        CapturingAccountService accountSvc = new CapturingAccountService();
        CapturingBudgetService  budgetSvc  = new CapturingBudgetService();

        Transaction result = new TransactionService(
                new DirectTxRunner(), txRepo, accountSvc, budgetSvc)
                .addTransaction(postedExpenseTx());

        assertNotNull(result, "saved transaction must be returned");
        assertEquals(1, txRepo.saved.size(),
                "transaction must be persisted exactly once");
        assertTrue(accountSvc.recomputedAccounts.contains(10L),
                "recomputeBalance must be called for accountId=10");
        assertFalse(accountSvc.recomputedAccounts.isEmpty(),
                "recomputeBalance must be triggered for a POSTED tx");
        assertTrue(budgetSvc.evaluatedCategoryIds.contains(5L),
                "evaluateEnvelopes must be called for categoryId=5 (POSTED EXPENSE)");
    }

    // ── Scenario B: exception propagation ────────────────────────────────────

    @Test
    void addTransaction_whenRecomputeThrows_exceptionPropagatesAndIsNotSwallowed() {
        RecordingSaveTxRepo txRepo         = new RecordingSaveTxRepo();
        CapturingAccountService accountSvc = new CapturingAccountService();
        accountSvc.throwOnRecompute = new RuntimeException("simulated recompute failure");

        TransactionService service = new TransactionService(
                new DirectTxRunner(), txRepo, accountSvc, new CapturingBudgetService());

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.addTransaction(postedExpenseTx()),
                "addTransaction must propagate the exception from recomputeBalance");
        assertEquals("simulated recompute failure", thrown.getMessage());

        // With DirectTxRunner there is no DB rollback, so the in-memory save side-effect
        // is visible here. This documents exactly what a real JdbcTxRunner would roll back
        // when a recompute failure occurs after the INSERT succeeds.
        assertEquals(1, txRepo.saved.size(),
                "save was called before the throw (in-memory stub is not rolled back)");
    }

    // ── Scenario C: PENDING skips balance and budget ──────────────────────────

    @Test
    void addTransaction_pendingExpense_persistsButSkipsRecomputeAndEvaluate() {
        RecordingSaveTxRepo txRepo         = new RecordingSaveTxRepo();
        CapturingAccountService accountSvc = new CapturingAccountService();
        CapturingBudgetService  budgetSvc  = new CapturingBudgetService();

        Transaction pendingTx = postedExpenseTx();
        pendingTx.setStatus(TxStatus.PENDING); // variable-income estimate — not yet confirmed

        new TransactionService(new DirectTxRunner(), txRepo, accountSvc, budgetSvc)
                .addTransaction(pendingTx);

        assertEquals(1, txRepo.saved.size(),
                "PENDING transaction must still be persisted");
        assertTrue(accountSvc.recomputedAccounts.isEmpty(),
                "recomputeBalance must NOT be called for a PENDING transaction");
        assertTrue(budgetSvc.evaluatedCategoryIds.isEmpty(),
                "evaluateEnvelopes must NOT be called for a PENDING transaction");
    }
}
