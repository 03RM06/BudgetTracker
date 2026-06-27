package com.budgettracker.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C2 — ConnectionContext (ThreadLocal) cleanup guarantees.
 *
 * ConnectionContext is package-private; this test lives in the same package to
 * access it directly.
 *
 * What IS tested here:
 *   - ConnectionContext.hasConnection() returns false on a clean thread.
 *   - bind() / unbind() round-trips work correctly.
 *   - requireConnection() throws when no connection is bound.
 *   - DirectTxRunner leaves the ThreadLocal untouched (it never calls bind/unbind).
 *
 * What requires a real DataSource (NOT tested here):
 *   JdbcTxRunner calls Database.dataSource(), which requires Database.init() to
 *   have been called with a real JDBC URL.  A full integration test would need
 *   either an in-memory H2 database or a Testcontainers MySQL container and would
 *   verify:
 *     1. ConnectionContext.hasConnection() is false before the transaction starts.
 *     2. Inside the work lambda, ConnectionContext.hasConnection() is true.
 *     3. After a successful commit, ConnectionContext.hasConnection() is false.
 *     4. After a failed work lambda (RuntimeException), hasConnection() is STILL
 *        false — the finally block in JdbcTxRunner always calls unbind().
 *
 *   The implementation guarantee is visible at JdbcTxRunner.java:
 *       } finally {
 *           ConnectionContext.unbind();   // ALWAYS clear ThreadLocal
 *           close(c);                     // ALWAYS return connection to pool
 *       }
 */
class JdbcTxRunnerThreadLocalTest {

    /**
     * Safety net: make sure the ThreadLocal is clean after every test so that
     * a bind() without unbind() in one test cannot poison a later test.
     */
    @AfterEach
    void ensureThreadLocalCleaned() {
        ConnectionContext.unbind(); // safe to call even if already unbound
    }

    // ── helper: no-op Connection via JDK dynamic proxy ────────────────────────

    private static Connection stubConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    // Return type-appropriate zero values; we never call methods on this stub.
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class)     return 0;
                    return null;
                });
    }

    // ── ConnectionContext behaviour ───────────────────────────────────────────

    @Test
    void hasConnection_returnsFalseOnFreshThread() {
        assertFalse(ConnectionContext.hasConnection(),
                "A thread that has never called bind() must report no connection");
    }

    @Test
    void bind_makesConnectionAvailable() {
        assertFalse(ConnectionContext.hasConnection(), "precondition: no connection bound");

        ConnectionContext.bind(stubConnection());

        assertTrue(ConnectionContext.hasConnection(),
                "After bind(), hasConnection() must return true");
    }

    @Test
    void unbind_clearsConnection() {
        ConnectionContext.bind(stubConnection());
        assertTrue(ConnectionContext.hasConnection(), "precondition: connection is bound");

        ConnectionContext.unbind();

        assertFalse(ConnectionContext.hasConnection(),
                "After unbind(), hasConnection() must return false");
    }

    @Test
    void requireConnection_throwsIllegalStateWhenNoBoundConnection() {
        assertFalse(ConnectionContext.hasConnection(), "precondition: nothing bound");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                ConnectionContext::requireConnection,
                "requireConnection() must throw when called outside a transaction");

        assertTrue(ex.getMessage().contains("TxRunner.inTransaction"),
                "Error message should mention TxRunner.inTransaction() to aid debugging");
    }

    @Test
    void requireConnection_returnsConnectionAfterBind() {
        Connection stub = stubConnection();
        ConnectionContext.bind(stub);

        Connection retrieved = ConnectionContext.requireConnection();

        assertSame(stub, retrieved,
                "requireConnection() must return exactly the connection that was bound");
    }

    // ── DirectTxRunner: no ThreadLocal side-effects ───────────────────────────

    @Test
    void directTxRunner_successfulWork_doesNotLeakConnectionState() {
        assertFalse(ConnectionContext.hasConnection(), "precondition: clean state");

        new DirectTxRunner().inTransaction(() -> "ok");

        assertFalse(ConnectionContext.hasConnection(),
                "DirectTxRunner must not leave a connection bound after successful work");
    }

    @Test
    void directTxRunner_failingWork_doesNotLeakConnectionState() {
        assertFalse(ConnectionContext.hasConnection(), "precondition: clean state");

        assertThrows(RuntimeException.class,
                () -> new DirectTxRunner().inTransaction(() -> {
                    throw new RuntimeException("boom");
                }));

        assertFalse(ConnectionContext.hasConnection(),
                "DirectTxRunner must not leave a connection bound after a failed work lambda");
    }

    @Test
    void directTxRunner_nestedCall_doesNotBindOrUnbind() {
        // Simulate what JdbcTxRunner.inTransaction() does when detecting a nested call:
        // it skips binding. DirectTxRunner never binds, so nesting is always transparent.
        assertFalse(ConnectionContext.hasConnection());

        DirectTxRunner runner = new DirectTxRunner();
        runner.inTransaction(() ->
                runner.inTransaction(() -> "inner")
        );

        assertFalse(ConnectionContext.hasConnection(),
                "Nested DirectTxRunner calls must leave ThreadLocal clean");
    }
}
