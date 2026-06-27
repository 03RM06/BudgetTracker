package com.budgettracker.service;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.BudgetEnvelope;
import com.budgettracker.domain.IncomeNature;
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
 * C1 — updateTransaction edge cases:
 *   A) Account change   → recomputeBalance called for BOTH old and new accountId (deduplicated).
 *   B) Category change  → evaluateEnvelopes called for BOTH old and new categoryId.
 *   C) INCOME→EXPENSE   → evaluateEnvelopes called ONLY for the new category (old had none).
 */
class UpdateTransactionTest {

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

    // ── minimal working stubs ─────────────────────────────────────────────────

    /**
     * findById serves the pre-loaded "old" state.
     * update() is a no-op (we assert via side-effects on capturing services).
     */
    private static class StubTxRepo extends ThrowTxRepo {
        final List<Transaction> store = new ArrayList<>();

        void add(Transaction t) { store.add(t); }

        @Override
        public Optional<Transaction> findById(long id) {
            return store.stream().filter(t -> t.getId() == id).findFirst();
        }

        @Override
        public void update(Transaction t) { /* no-op */ }
    }

    /** Records each call to recomputeBalance(accountId). */
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

    /** Records every categoryId passed to evaluateEnvelopes. */
    private static class CapturingBudgetService extends BudgetService {
        final List<Long> calledCategoryIds = new ArrayList<>();

        CapturingBudgetService() {
            super(new DirectTxRunner(), new ThrowEnvelopeRepo(), new ThrowTxRepo());
        }

        @Override
        public List<EnvelopeStatus> evaluateEnvelopes(long userId, long categoryId, LocalDate date) {
            calledCategoryIds.add(categoryId);
            return List.of();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Transaction incomeTx(long id, long accountId) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setUserId(1L);
        t.setAccountId(accountId);
        t.setDirection(TxDirection.INCOME);
        t.setIncomeNature(IncomeNature.FIXED);
        t.setStatus(TxStatus.POSTED);
        t.setAmount(new BigDecimal("100.00"));
        t.setCurrency("PHP");
        t.setOccurredOn(LocalDate.of(2026, 6, 1));
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return t;
    }

    private Transaction expenseTx(long id, long accountId, Long categoryId) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setUserId(1L);
        t.setAccountId(accountId);
        t.setDirection(TxDirection.EXPENSE);
        t.setStatus(TxStatus.POSTED);
        t.setAmount(new BigDecimal("100.00"));
        t.setCurrency("PHP");
        t.setCategoryId(categoryId);
        t.setOccurredOn(LocalDate.of(2026, 6, 1));
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return t;
    }

    // ── Scenario A: account change recomputes both accounts ───────────────────

    @Test
    void updateTransaction_accountChange_recomputesBothOldAndNewAccount() {
        // old: accountId=1, INCOME
        Transaction old = incomeTx(1L, 1L);

        StubTxRepo txRepo = new StubTxRepo();
        txRepo.add(old);

        // updated: same id, new accountId=2
        Transaction updated = incomeTx(1L, 2L);

        CapturingAccountService acctSvc = new CapturingAccountService();

        new TransactionService(new DirectTxRunner(), txRepo, acctSvc, new CapturingBudgetService())
                .updateTransaction(updated);

        assertTrue(acctSvc.recomputedAccounts.contains(1L),
                "old accountId=1 must be recomputed after an account-change update");
        assertTrue(acctSvc.recomputedAccounts.contains(2L),
                "new accountId=2 must be recomputed after an account-change update");
        assertEquals(2, acctSvc.recomputedAccounts.size(),
                "exactly two recompute calls — Set deduplication must not drop distinct ids");
    }

    @Test
    void updateTransaction_sameAccount_recomputesOnce() {
        // Both old and updated share accountId=1 — the Set collapses them to one call.
        Transaction old = incomeTx(1L, 1L);

        StubTxRepo txRepo = new StubTxRepo();
        txRepo.add(old);

        Transaction updated = incomeTx(1L, 1L); // same account, different (but valid) state

        CapturingAccountService acctSvc = new CapturingAccountService();

        new TransactionService(new DirectTxRunner(), txRepo, acctSvc, new CapturingBudgetService())
                .updateTransaction(updated);

        assertEquals(1, acctSvc.recomputedAccounts.size(),
                "when account did not change, the Set deduplicates to a single recompute call");
        assertEquals(1L, acctSvc.recomputedAccounts.get(0));
    }

    // ── Scenario B: category change evaluates both old and new category ────────

    @Test
    void updateTransaction_categoryChange_evaluatesOldAndNewCategories() {
        // old: accountId=1, EXPENSE, categoryId=10
        Transaction old = expenseTx(1L, 1L, 10L);

        StubTxRepo txRepo = new StubTxRepo();
        txRepo.add(old);

        // updated: same account, EXPENSE, but categoryId=20
        Transaction updated = expenseTx(1L, 1L, 20L);

        CapturingBudgetService budgSvc = new CapturingBudgetService();

        new TransactionService(new DirectTxRunner(), txRepo, new CapturingAccountService(), budgSvc)
                .updateTransaction(updated);

        assertTrue(budgSvc.calledCategoryIds.contains(10L),
                "old categoryId=10 must be re-evaluated so its budget status is refreshed");
        assertTrue(budgSvc.calledCategoryIds.contains(20L),
                "new categoryId=20 must be evaluated since spending just moved into it");
        assertEquals(2, budgSvc.calledCategoryIds.size(),
                "exactly two evaluateEnvelopes calls — one for each distinct category");
    }

    // ── Scenario C: INCOME→EXPENSE only evaluates the new category ───────────

    @Test
    void updateTransaction_directionChangeIncomeToExpense_evaluatesOnlyNewCategory() {
        // old: INCOME with no category
        Transaction old = incomeTx(1L, 1L);
        old.setCategoryId(null);

        StubTxRepo txRepo = new StubTxRepo();
        txRepo.add(old);

        // updated: EXPENSE with categoryId=10
        Transaction updated = expenseTx(1L, 1L, 10L);

        CapturingBudgetService budgSvc = new CapturingBudgetService();

        new TransactionService(new DirectTxRunner(), txRepo, new CapturingAccountService(), budgSvc)
                .updateTransaction(updated);

        assertEquals(1, budgSvc.calledCategoryIds.size(),
                "only the new expense category should be evaluated — old tx had no category");
        assertEquals(10L, budgSvc.calledCategoryIds.get(0),
                "evaluateEnvelopes must use the new categoryId=10");
    }
}
