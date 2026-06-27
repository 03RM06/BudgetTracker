package com.budgettracker.service;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.BudgetEnvelope;
import com.budgettracker.domain.IncomeNature;
import com.budgettracker.domain.InvalidTransactionException;
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
 * C1 — confirmPending / cancelPending lifecycle:
 *   - confirmPending happy path: status → POSTED, amount updated, recomputeBalance called.
 *   - confirmPending on a non-PENDING tx: throws InvalidTransactionException.
 *   - cancelPending: status → CANCELLED, recomputeBalance NOT called.
 */
class PendingTransactionTest {

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

    // ── minimal working stubs ─────────────────────────────────────────────────

    /**
     * TransactionRepository that supports findById and records update() calls.
     * All other methods throw to surface accidental usage.
     */
    private static class CapturingTxRepo implements TransactionRepository {
        final List<Transaction> store   = new ArrayList<>();
        final List<Transaction> updates = new ArrayList<>();

        void add(Transaction t) { store.add(t); }

        @Override
        public Optional<Transaction> findById(long id) {
            return store.stream().filter(t -> t.getId() == id).findFirst();
        }

        @Override
        public void update(Transaction t) {
            updates.add(t);
        }

        @Override public Transaction save(Transaction t) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByUserId(long u) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByAccountId(long a) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByUserIdAndDateRange(long u, LocalDate f, LocalDate t) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByUserIdCategoryAndDateRange(long u, long c, LocalDate f, LocalDate t) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByRecurringId(long r) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findTransferCreditsForAccount(long a) { throw new UnsupportedOperationException(); }
        @Override public void deleteById(long id) { throw new UnsupportedOperationException(); }
    }

    /** Records every call to recomputeBalance(). */
    private static class CapturingAccountService extends AccountService {
        final List<Long> recomputedAccounts = new ArrayList<>();

        CapturingAccountService() {
            super(new DirectTxRunner(), new ThrowAccountRepo(), new CapturingTxRepo());
        }

        @Override
        public void recomputeBalance(long accountId) {
            recomputedAccounts.add(accountId);
        }
    }

    /** Records every call to evaluateEnvelopes(). */
    private static class CapturingBudgetService extends BudgetService {
        final List<Long> calledCategoryIds = new ArrayList<>();

        CapturingBudgetService() {
            super(new DirectTxRunner(), new ThrowEnvelopeRepo(), new CapturingTxRepo());
        }

        @Override
        public List<EnvelopeStatus> evaluateEnvelopes(long userId, long categoryId, LocalDate date) {
            calledCategoryIds.add(categoryId);
            return List.of();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Transaction pendingIncomeTx(long id, long accountId) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setUserId(1L);
        t.setAccountId(accountId);
        t.setDirection(TxDirection.INCOME);
        t.setIncomeNature(IncomeNature.VARIABLE); // variable income is the canonical PENDING use-case
        t.setStatus(TxStatus.PENDING);
        t.setAmount(new BigDecimal("100.00")); // estimate; will be overwritten by confirmPending
        t.setCurrency("PHP");
        t.setOccurredOn(LocalDate.of(2026, 6, 15));
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return t;
    }

    // ── confirmPending: happy path ────────────────────────────────────────────

    @Test
    void confirmPending_pendingIncome_setsPostedWithActualAmountAndRecomputes() {
        CapturingTxRepo txRepo = new CapturingTxRepo();
        txRepo.add(pendingIncomeTx(1L, 10L));

        CapturingAccountService acctSvc = new CapturingAccountService();
        CapturingBudgetService  budgSvc = new CapturingBudgetService();

        TransactionService service = new TransactionService(
                new DirectTxRunner(), txRepo, acctSvc, budgSvc);

        Transaction confirmed = service.confirmPending(1L, new BigDecimal("150.00"));

        // Status must flip to POSTED
        assertEquals(TxStatus.POSTED, confirmed.getStatus(),
                "confirmPending must change status to POSTED");

        // Amount must be overwritten with the actual value
        assertEquals(0, confirmed.getAmount().compareTo(new BigDecimal("150.00")),
                "confirmPending must set the actual amount on the transaction");

        // update() must have been called so the new state is persisted
        assertEquals(1, txRepo.updates.size(),
                "update() must be called once to persist the confirmed state");
        assertEquals(TxStatus.POSTED, txRepo.updates.get(0).getStatus());

        // recomputeBalance must be triggered to reflect the newly POSTED income
        assertTrue(acctSvc.recomputedAccounts.contains(10L),
                "recomputeBalance must be called for the account after confirmation");

        // evaluateEnvelopes must NOT be called — this is an INCOME, not an EXPENSE
        assertTrue(budgSvc.calledCategoryIds.isEmpty(),
                "evaluateEnvelopes must NOT be called when confirming an INCOME transaction");
    }

    @Test
    void confirmPending_positiveAmountValidationWorks() {
        // Invalid amount must be rejected before touching the repository
        TransactionService service = new TransactionService(
                new DirectTxRunner(), new CapturingTxRepo(),
                new CapturingAccountService(), new CapturingBudgetService());

        assertThrows(InvalidTransactionException.class,
                () -> service.confirmPending(1L, BigDecimal.ZERO),
                "confirmPending must reject amount = 0");

        assertThrows(InvalidTransactionException.class,
                () -> service.confirmPending(1L, new BigDecimal("-1.00")),
                "confirmPending must reject a negative amount");
    }

    // ── confirmPending: non-PENDING tx throws ─────────────────────────────────

    @Test
    void confirmPending_onPostedTransaction_throwsInvalidTransactionException() {
        Transaction postedTx = pendingIncomeTx(2L, 10L);
        postedTx.setStatus(TxStatus.POSTED); // already posted

        CapturingTxRepo txRepo = new CapturingTxRepo();
        txRepo.add(postedTx);

        TransactionService service = new TransactionService(
                new DirectTxRunner(), txRepo,
                new CapturingAccountService(), new CapturingBudgetService());

        assertThrows(InvalidTransactionException.class,
                () -> service.confirmPending(2L, new BigDecimal("200.00")),
                "confirmPending on a POSTED transaction must throw InvalidTransactionException");
    }

    @Test
    void confirmPending_onCancelledTransaction_throwsInvalidTransactionException() {
        Transaction cancelledTx = pendingIncomeTx(3L, 10L);
        cancelledTx.setStatus(TxStatus.CANCELLED);

        CapturingTxRepo txRepo = new CapturingTxRepo();
        txRepo.add(cancelledTx);

        TransactionService service = new TransactionService(
                new DirectTxRunner(), txRepo,
                new CapturingAccountService(), new CapturingBudgetService());

        assertThrows(InvalidTransactionException.class,
                () -> service.confirmPending(3L, new BigDecimal("200.00")),
                "confirmPending on a CANCELLED transaction must throw InvalidTransactionException");
    }

    // ── cancelPending: happy path ─────────────────────────────────────────────

    @Test
    void cancelPending_pendingTransaction_setsCancelledAndDoesNotRecompute() {
        CapturingTxRepo txRepo = new CapturingTxRepo();
        txRepo.add(pendingIncomeTx(4L, 10L));

        CapturingAccountService acctSvc = new CapturingAccountService();
        CapturingBudgetService  budgSvc = new CapturingBudgetService();

        new TransactionService(new DirectTxRunner(), txRepo, acctSvc, budgSvc)
                .cancelPending(4L);

        // Status must be written as CANCELLED
        assertEquals(1, txRepo.updates.size(),
                "update() must be called once to persist the cancellation");
        assertEquals(TxStatus.CANCELLED, txRepo.updates.get(0).getStatus(),
                "cancelPending must set status to CANCELLED");

        // PENDING transactions do not affect balances, so no recompute is needed
        assertTrue(acctSvc.recomputedAccounts.isEmpty(),
                "cancelPending must NOT call recomputeBalance (PENDING tx never affected the balance)");

        // No envelopes to re-evaluate either
        assertTrue(budgSvc.calledCategoryIds.isEmpty(),
                "cancelPending must NOT call evaluateEnvelopes");
    }

    @Test
    void cancelPending_onPostedTransaction_throwsInvalidTransactionException() {
        Transaction postedTx = pendingIncomeTx(5L, 10L);
        postedTx.setStatus(TxStatus.POSTED);

        CapturingTxRepo txRepo = new CapturingTxRepo();
        txRepo.add(postedTx);

        TransactionService service = new TransactionService(
                new DirectTxRunner(), txRepo,
                new CapturingAccountService(), new CapturingBudgetService());

        assertThrows(InvalidTransactionException.class,
                () -> service.cancelPending(5L),
                "cancelPending on a POSTED transaction must throw InvalidTransactionException");
    }
}
