package com.budgettracker.persistence;

import java.util.function.Supplier;

public class DirectTxRunner implements TxRunner {
    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }
}
