package com.budgettracker.persistence;

import java.util.function.Supplier;

public interface TxRunner {
    <T> T inTransaction(Supplier<T> work);
    default void inTransaction(Runnable work) {
        inTransaction(() -> { work.run(); return null; });
    }
}
