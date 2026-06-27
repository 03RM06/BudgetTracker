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
 * C1 — deleteTransaction envelope re-evaluation contract:
 *   - EXPENSE with a non-null categoryId → evaluateEnvelopes called with the
 *     correct (userId, categoryId, occurredOn) triple.
 *   - INCOME (categoryId null) → evaluateEnvelopes must NOT be called.
 */
class DeleteTransactionTest {

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

    // ── minimal working stubs ─────────────────────────────────────────────────

    /** Holds a fixed set of transactions; supports findById and deleteById. */
    private static class StubTxRepo extends ThrowTxRepo {
        final List<Transaction> store = new ArrayList<>();

        @Override
        public Optional<Transaction> findById(long id) {
            return store.stream().filter(t -> t.getId() == id).findFirst();
        }

        @Override
        public void deleteById(long id) {
            store.removeIf(t -> t.getId() == id);
        }

        @Override
        public void update(Transaction t) { /* no-op */ }
    }

    /** Records every call to recomputeBalance. */
    private static class CapturingAccountService extends AccountService {
        final List<Long> recomputedAccounts = new ArrayList<>();

        CapturingAccountService() {
            super(new DirectTxRunner(), new ThrowAccountRepo(), new ThrowTxRepo());
        }

        @Override
        public void recomputeBalance(long accountId) {
            recomputedAccounts.add(accountId);
        }
    }

    /** Records all arguments passed to evaluateEnvelopes. */
    private static class CapturingBudgetService extends BudgetService {
        final List<Long>      calledUserIds     = new ArrayList<>();
        final List<Long>      calledCategoryIds = new ArrayList<>();
        final List<LocalDate> calledDates       = new ArrayList<>();

        CapturingBudgetService() {
            super(new DirectTxRunner(), new ThrowEnvelopeRepo(), new ThrowTxRepo());
        }

        @Override
        public List<EnvelopeStatus> evaluateEnvelopes(long userId, long categoryId, LocalDate date) {
            calledUserIds.add(userId);
            calledCategoryIds.add(categoryId);
            calledDates.add(date);
            return List.of();
        }
    }

    // ── Scenario 1: EXPENSE with categoryId → evaluateEnvelopes called ────────

    @Test
    void deleteTransaction_expense_callsEvaluateEnvelopesWithCorrectArgs() {
        LocalDate txDate = LocalDate.of(2026, 6, 15);

        Transaction tx = new Transaction();
        tx.setId(1L);
        tx.setUserId(1L);
        tx.setAccountId(10L);
        tx.setDirection(TxDirection.EXPENSE);
        tx.setStatus(TxStatus.POSTED);
        tx.setAmount(new BigDecimal("100.00"));
        tx.setCategoryId(5L);
        tx.setOccurredOn(txDate);
        tx.setCreatedAt(Instant.now());
        tx.setUpdatedAt(Instant.now());

        StubTxRepo txRepo               = new StubTxRepo();
        CapturingAccountService acctSvc = new CapturingAccountService();
        CapturingBudgetService  budgSvc = new CapturingBudgetService();
        txRepo.store.add(tx);

        new TransactionService(new DirectTxRunner(), txRepo, acctSvc, budgSvc)
                .deleteTransaction(1L);

        // recompute was triggered for the account
        assertEquals(1, acctSvc.recomputedAccounts.size());
        assertEquals(10L, acctSvc.recomputedAccounts.get(0));

        // evaluateEnvelopes was called exactly once with the right args
        assertEquals(1, budgSvc.calledCategoryIds.size(),
                "evaluateEnvelopes should be called exactly once for an EXPENSE delete");
        assertEquals(1L, budgSvc.calledUserIds.get(0),
                "userId must match the transaction's userId");
        assertEquals(5L, budgSvc.calledCategoryIds.get(0),
                "categoryId must match the transaction's categoryId");
        assertEquals(txDate, budgSvc.calledDates.get(0),
                "date must match the transaction's occurredOn");
    }

    // ── Scenario 2: INCOME (no category) → evaluateEnvelopes NOT called ───────

    @Test
    void deleteTransaction_income_doesNotCallEvaluateEnvelopes() {
        Transaction tx = new Transaction();
        tx.setId(2L);
        tx.setUserId(1L);
        tx.setAccountId(10L);
        tx.setDirection(TxDirection.INCOME);
        tx.setStatus(TxStatus.POSTED);
        tx.setAmount(new BigDecimal("500.00"));
        tx.setCategoryId(null); // INCOME carries no expense category
        tx.setOccurredOn(LocalDate.of(2026, 6, 15));
        tx.setCreatedAt(Instant.now());
        tx.setUpdatedAt(Instant.now());

        StubTxRepo txRepo               = new StubTxRepo();
        CapturingAccountService acctSvc = new CapturingAccountService();
        CapturingBudgetService  budgSvc = new CapturingBudgetService();
        txRepo.store.add(tx);

        new TransactionService(new DirectTxRunner(), txRepo, acctSvc, budgSvc)
                .deleteTransaction(2L);

        // balance recompute still happens for INCOME deletes
        assertFalse(acctSvc.recomputedAccounts.isEmpty(),
                "recomputeBalance should still run when deleting an INCOME tx");

        // but envelope evaluation must be skipped entirely
        assertTrue(budgSvc.calledCategoryIds.isEmpty(),
                "evaluateEnvelopes must NOT be called when deleting an INCOME transaction");
    }
}
