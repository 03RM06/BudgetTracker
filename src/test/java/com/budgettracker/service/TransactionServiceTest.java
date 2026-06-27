package com.budgettracker.service;

import com.budgettracker.domain.Account;
import com.budgettracker.domain.BudgetEnvelope;
import com.budgettracker.domain.IncomeNature;
import com.budgettracker.domain.InvalidTransactionException;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
import com.budgettracker.persistence.AccountRepository;
import com.budgettracker.persistence.BudgetEnvelopeRepository;
import com.budgettracker.persistence.DirectTxRunner;
import com.budgettracker.persistence.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest {

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

    // ── helper ────────────────────────────────────────────────────────────────

    private TransactionService service() {
        return new TransactionService(
                new DirectTxRunner(),
                new ThrowTxRepo(),
                new AccountService(new DirectTxRunner(), new ThrowAccountRepo(), new ThrowTxRepo()),
                new BudgetService(new DirectTxRunner(), new ThrowEnvelopeRepo(), new ThrowTxRepo()));
    }

    private Transaction baseTx(TxDirection direction) {
        Transaction t = new Transaction();
        t.setUserId(1L);
        t.setAccountId(10L);
        t.setDirection(direction);
        t.setAmount(new BigDecimal("100.00"));
        t.setCurrency("PHP");
        t.setOccurredOn(LocalDate.of(2026, 6, 1));
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return t;
    }

    // ── addTransaction: input validation ──────────────────────────────────────

    @Test
    void addTransaction_nullAmountThrows() {
        Transaction tx = baseTx(TxDirection.EXPENSE);
        tx.setAmount(null);
        assertThrows(InvalidTransactionException.class, () -> service().addTransaction(tx));
    }

    @Test
    void addTransaction_zeroAmountThrows() {
        Transaction tx = baseTx(TxDirection.EXPENSE);
        tx.setAmount(BigDecimal.ZERO);
        assertThrows(InvalidTransactionException.class, () -> service().addTransaction(tx));
    }

    @Test
    void addTransaction_negativeAmountThrows() {
        Transaction tx = baseTx(TxDirection.EXPENSE);
        tx.setAmount(new BigDecimal("-1"));
        assertThrows(InvalidTransactionException.class, () -> service().addTransaction(tx));
    }

    @Test
    void addTransaction_incomeWithNullIncomeNatureThrows() {
        Transaction tx = baseTx(TxDirection.INCOME);
        tx.setIncomeNature(null);
        assertThrows(InvalidTransactionException.class, () -> service().addTransaction(tx));
    }

    @Test
    void addTransaction_incomeWithIncomeNatureSucceedsValidation() {
        // Validation passes — repo throws because it is a throw-all stub.
        // Assert that InvalidTransactionException is NOT the cause.
        Transaction tx = baseTx(TxDirection.INCOME);
        tx.setIncomeNature(IncomeNature.FIXED);
        assertThrows(RuntimeException.class, () -> service().addTransaction(tx));
        // Specifically NOT InvalidTransactionException (validation would have passed).
        try {
            service().addTransaction(tx);
        } catch (InvalidTransactionException e) {
            fail("Valid INCOME tx with incomeNature set should pass validation");
        } catch (RuntimeException ignored) {
            // expected from the throw-all txRepo.save
        }
    }

    @Test
    void addTransaction_transferWithNullTransferAccountIdThrows() {
        Transaction tx = baseTx(TxDirection.TRANSFER);
        tx.setTransferAccountId(null);
        assertThrows(InvalidTransactionException.class, () -> service().addTransaction(tx));
    }

    @Test
    void addTransaction_transferToSameAccountThrows() {
        Transaction tx = baseTx(TxDirection.TRANSFER);
        tx.setTransferAccountId(10L); // same as accountId
        assertThrows(InvalidTransactionException.class, () -> service().addTransaction(tx));
    }
}
