package com.budgettracker.service;

import com.budgettracker.domain.BudgetEnvelope;
import com.budgettracker.domain.BudgetStatus;
import com.budgettracker.domain.PeriodType;
import com.budgettracker.domain.Transaction;
import com.budgettracker.domain.TxDirection;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BudgetServiceTest {

    // ── stubs ─────────────────────────────────────────────────────────────────

    private static class StubEnvelopeRepo implements BudgetEnvelopeRepository {
        final List<BudgetEnvelope> store = new ArrayList<>();
        BudgetEnvelope saved;

        @Override public BudgetEnvelope save(BudgetEnvelope e) { saved = e; return e; }
        @Override public Optional<BudgetEnvelope> findById(long id) {
            return store.stream().filter(e -> e.getId() == id).findFirst();
        }
        @Override public List<BudgetEnvelope> findByUserId(long userId) {
            return store.stream().filter(e -> e.getUserId() == userId).collect(Collectors.toList());
        }
        @Override public List<BudgetEnvelope> findActiveByUserIdAndDate(long userId, LocalDate date) {
            return store.stream()
                    .filter(e -> e.getUserId() == userId && e.isActive()
                            && !date.isBefore(e.getPeriodStart()) && !date.isAfter(e.getPeriodEnd()))
                    .collect(Collectors.toList());
        }
        @Override public Optional<BudgetEnvelope> findByCategoryAndPeriodStart(long userId, long categoryId, LocalDate periodStart) {
            return store.stream()
                    .filter(e -> e.getUserId() == userId && e.getCategoryId() == categoryId
                            && e.getPeriodStart().equals(periodStart))
                    .findFirst();
        }
        @Override public void update(BudgetEnvelope e) {}
        @Override public void deleteById(long id) {}
    }

    private static class StubTxRepo implements TransactionRepository {
        final List<Transaction> all = new ArrayList<>();

        @Override public Transaction save(Transaction t) { return t; }
        @Override public Optional<Transaction> findById(long id) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByUserId(long u) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByAccountId(long a) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByUserIdAndDateRange(long u, LocalDate f, LocalDate t) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findByUserIdCategoryAndDateRange(long userId, long categoryId, LocalDate from, LocalDate to) {
            return all.stream()
                    .filter(t -> t.getUserId() == userId
                            && t.getCategoryId() != null && t.getCategoryId() == categoryId
                            && !t.getOccurredOn().isBefore(from)
                            && !t.getOccurredOn().isAfter(to))
                    .collect(Collectors.toList());
        }
        @Override public List<Transaction> findByRecurringId(long r) { throw new UnsupportedOperationException(); }
        @Override public List<Transaction> findTransferCreditsForAccount(long a) { throw new UnsupportedOperationException(); }
        @Override public void update(Transaction t) {}
        @Override public void deleteById(long id) {}
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private BudgetEnvelope envelope(long id, long userId, long categoryId,
                                    LocalDate start, LocalDate end,
                                    BigDecimal limit, boolean rollover) {
        BudgetEnvelope e = new BudgetEnvelope();
        e.setId(id);
        e.setUserId(userId);
        e.setCategoryId(categoryId);
        e.setName("Envelope-" + id);
        e.setPeriodType(PeriodType.MONTHLY);
        e.setPeriodStart(start);
        e.setPeriodEnd(end);
        e.setLimitAmount(limit);
        e.setAlertThresholdPct(new BigDecimal("80"));
        e.setRollover(rollover);
        e.setZoneId("Asia/Manila");
        e.setActive(true);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    private Transaction expenseTx(long userId, long categoryId, LocalDate date, BigDecimal amount) {
        Transaction t = new Transaction();
        t.setUserId(userId);
        t.setCategoryId(categoryId);
        t.setDirection(TxDirection.EXPENSE);
        t.setAmount(amount);
        t.setOccurredOn(date);
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return t;
    }

    // ── evaluate: alert states ─────────────────────────────────────────────────

    @Test
    void evaluate_ok_whenSpentBelowThreshold() {
        StubEnvelopeRepo envelopeRepo = new StubEnvelopeRepo();
        StubTxRepo txRepo = new StubTxRepo();
        BudgetEnvelope env = envelope(1L, 1L, 100L,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                new BigDecimal("1000"), false);
        txRepo.all.add(expenseTx(1L, 100L, LocalDate.of(2026, 6, 10), new BigDecimal("500")));

        EnvelopeStatus status = new BudgetService(new DirectTxRunner(), envelopeRepo, txRepo).evaluate(env);

        assertEquals(BudgetStatus.OK, status.getStatus());
        assertEquals(0, status.getSpent().compareTo(new BigDecimal("500")));
    }

    @Test
    void evaluate_warn_whenSpentAtThreshold() {
        StubEnvelopeRepo envelopeRepo = new StubEnvelopeRepo();
        StubTxRepo txRepo = new StubTxRepo();
        BudgetEnvelope env = envelope(1L, 1L, 100L,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                new BigDecimal("1000"), false);
        txRepo.all.add(expenseTx(1L, 100L, LocalDate.of(2026, 6, 10), new BigDecimal("800")));

        EnvelopeStatus status = new BudgetService(new DirectTxRunner(), envelopeRepo, txRepo).evaluate(env);

        assertEquals(BudgetStatus.WARN, status.getStatus(),
                "Spent exactly at 80% threshold should be WARN");
    }

    @Test
    void evaluate_over_whenSpentExceedsLimit() {
        StubEnvelopeRepo envelopeRepo = new StubEnvelopeRepo();
        StubTxRepo txRepo = new StubTxRepo();
        BudgetEnvelope env = envelope(1L, 1L, 100L,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                new BigDecimal("1000"), false);
        txRepo.all.add(expenseTx(1L, 100L, LocalDate.of(2026, 6, 10), new BigDecimal("1200")));

        EnvelopeStatus status = new BudgetService(new DirectTxRunner(), envelopeRepo, txRepo).evaluate(env);

        assertEquals(BudgetStatus.OVER, status.getStatus());
    }

    // ── evaluate: rollover ────────────────────────────────────────────────────

    @Test
    void evaluate_rollover_addsUnspentFromPriorPeriodToEffectiveLimit() {
        StubEnvelopeRepo envelopeRepo = new StubEnvelopeRepo();
        StubTxRepo txRepo = new StubTxRepo();

        BudgetEnvelope prior = envelope(1L, 1L, 100L,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                new BigDecimal("1000"), true);
        BudgetEnvelope current = envelope(2L, 1L, 100L,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                new BigDecimal("1000"), true);

        envelopeRepo.store.add(prior);
        envelopeRepo.store.add(current);

        // prior: spent 600 → remaining 400
        txRepo.all.add(expenseTx(1L, 100L, LocalDate.of(2026, 5, 15), new BigDecimal("600")));
        // current: spent 500
        txRepo.all.add(expenseTx(1L, 100L, LocalDate.of(2026, 6, 15), new BigDecimal("500")));

        EnvelopeStatus status = new BudgetService(new DirectTxRunner(), envelopeRepo, txRepo).evaluate(current);

        assertEquals(0, status.getEffectiveLimit().compareTo(new BigDecimal("1400")),
                "effectiveLimit = currentLimit(1000) + priorRemaining(400)");
        assertEquals(0, status.getRemaining().compareTo(new BigDecimal("900")));
        assertEquals(BudgetStatus.OK, status.getStatus());
    }

    // ── createEnvelope: period-end computation ────────────────────────────────

    @Test
    void createEnvelope_weekly_periodEndIsSixDaysAfterStart() {
        StubEnvelopeRepo envelopeRepo = new StubEnvelopeRepo();
        new BudgetService(new DirectTxRunner(), envelopeRepo, new StubTxRepo())
                .createEnvelope(1L, 100L, "Food", PeriodType.WEEKLY,
                        LocalDate.of(2026, 6, 1), new BigDecimal("500"),
                        false, new BigDecimal("80"), "Asia/Manila");

        assertEquals(LocalDate.of(2026, 6, 7), envelopeRepo.saved.getPeriodEnd());
    }

    @Test
    void createEnvelope_monthly_periodEndIsLastDayOfMonth() {
        StubEnvelopeRepo envelopeRepo = new StubEnvelopeRepo();
        new BudgetService(new DirectTxRunner(), envelopeRepo, new StubTxRepo())
                .createEnvelope(1L, 100L, "Food", PeriodType.MONTHLY,
                        LocalDate.of(2026, 6, 1), new BigDecimal("500"),
                        false, new BigDecimal("80"), "Asia/Manila");

        assertEquals(LocalDate.of(2026, 6, 30), envelopeRepo.saved.getPeriodEnd());
    }

    @Test
    void createEnvelope_yearly_periodEndIsDecember31() {
        StubEnvelopeRepo envelopeRepo = new StubEnvelopeRepo();
        new BudgetService(new DirectTxRunner(), envelopeRepo, new StubTxRepo())
                .createEnvelope(1L, 100L, "Food", PeriodType.YEARLY,
                        LocalDate.of(2026, 6, 1), new BigDecimal("5000"),
                        false, new BigDecimal("80"), "Asia/Manila");

        assertEquals(LocalDate.of(2026, 12, 31), envelopeRepo.saved.getPeriodEnd());
    }

    @Test
    void createEnvelope_customPeriodTypeThrows() {
        BudgetService svc = new BudgetService(new DirectTxRunner(), new StubEnvelopeRepo(), new StubTxRepo());
        assertThrows(IllegalArgumentException.class,
                () -> svc.createEnvelope(1L, 100L, "Food", PeriodType.CUSTOM,
                        LocalDate.of(2026, 6, 1), new BigDecimal("500"),
                        false, new BigDecimal("80"), "Asia/Manila"));
    }

    @Test
    void createEnvelope_zeroLimitThrows() {
        BudgetService svc = new BudgetService(new DirectTxRunner(), new StubEnvelopeRepo(), new StubTxRepo());
        assertThrows(IllegalArgumentException.class,
                () -> svc.createEnvelope(1L, 100L, "Food", PeriodType.MONTHLY,
                        LocalDate.of(2026, 6, 1), BigDecimal.ZERO,
                        false, new BigDecimal("80"), "Asia/Manila"));
    }
}
