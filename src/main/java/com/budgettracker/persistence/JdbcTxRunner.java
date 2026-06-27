package com.budgettracker.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

public class JdbcTxRunner implements TxRunner {

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        if (ConnectionContext.hasConnection()) {
            return work.get();   // nested call: join the existing transaction
        }
        Connection c = null;
        try {
            c = Database.dataSource().getConnection();
            c.setAutoCommit(false);
            ConnectionContext.bind(c);
            T result = work.get();
            c.commit();
            return result;
        } catch (RuntimeException e) {
            rollback(c);
            throw e;
        } catch (SQLException e) {
            rollback(c);
            throw new DataAccessException("Transaction failed", e);
        } finally {
            ConnectionContext.unbind();   // ALWAYS clear ThreadLocal
            close(c);                     // ALWAYS return connection to pool
        }
    }

    private static void rollback(Connection c) {
        if (c == null) return;
        try { c.rollback(); } catch (SQLException ignored) {}
    }

    private static void close(Connection c) {
        if (c == null) return;
        try { c.close(); } catch (SQLException ignored) {}
    }
}
