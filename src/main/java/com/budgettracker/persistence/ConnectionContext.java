package com.budgettracker.persistence;

import java.sql.Connection;

final class ConnectionContext {
    private static final ThreadLocal<Connection> CURRENT = new ThreadLocal<>();

    private ConnectionContext() {}

    static boolean hasConnection() {
        return CURRENT.get() != null;
    }

    static Connection requireConnection() {
        Connection c = CURRENT.get();
        if (c == null) throw new IllegalStateException(
            "No JDBC connection bound to current thread — call must be inside TxRunner.inTransaction()");
        return c;
    }

    static void bind(Connection c) { CURRENT.set(c); }

    static void unbind() { CURRENT.remove(); }
}
