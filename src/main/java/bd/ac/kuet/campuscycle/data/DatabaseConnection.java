package bd.ac.kuet.campuscycle.data;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Production-grade pooled JDBC connection provider for CampusCycle.
 * Reuses active TCP/TLS 1.3 connections to Supabase PostgreSQL, eliminating the
 * ~600ms–800ms handshake overhead on individual queries.
 *
 * <p>Credentials are never committed to source (P-004, P-159): the DB user/password
 * come from the environment or {@code .env} via {@link bd.ac.kuet.campuscycle.config.ClientConfig}
 * and construction throws when they are absent. Only host/port/database have safe defaults.
 *
 * <p>Query discipline (P-150): repositories should build every statement through
 * {@link #prepare(Connection, String)}, which bounds server execution with
 * {@code setQueryTimeout(5)} so one slow query cannot pin a pool slot indefinitely.
 */
public final class DatabaseConnection {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConnection.class.getName());

    private static final String DEFAULT_HOST = "aws-0-ap-northeast-1.pooler.supabase.com";
    private static final int DEFAULT_PORT = 5432;
    private static final String DEFAULT_DATABASE = "postgres";

    private static final int MAX_POOL_SIZE = 10;
    private static final long MAX_LIFETIME_MS = 8 * 60 * 1000L; // 8 minutes (before Supabase PgBouncer closes idle socket)
    private static final long MAX_IDLE_MS = 60 * 1000L; // 60 seconds
    private static final int ACQUIRE_TIMEOUT_SEC = 10;

    private static final BlockingQueue<PooledEntry> POOL = new LinkedBlockingQueue<>();
    private static final AtomicInteger TOTAL_CONNECTIONS = new AtomicInteger(0);

    private static volatile Boolean available = null;
    private static volatile long lastCheckedMs = 0L;
    private static volatile String lastErrorDetail = "never probed";
    private static final long SUCCESS_TTL_MS = 30_000L;
    private static final long RETRY_INTERVAL_MS = 15_000L;

    /**
     * Human-readable reason for the last failed availability probe
     * (or "never probed" / "ok"). Surfaced in OFFLINE errors so users
     * see the actual cause instead of a generic message.
     */
    public static String lastErrorDetail() {
        return lastErrorDetail;
    }

    static {
        // Clean shutdown hook to close pooled physical connections on exit
        Runtime.getRuntime().addShutdownHook(new Thread(DatabaseConnection::closeAllPhysical, "cc-db-pool-shutdown"));
    }

    private record PooledEntry(Connection physical, long createdMs, long lastUsedMs) {
        PooledEntry touch() {
            return new PooledEntry(physical, createdMs, System.currentTimeMillis());
        }
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            v = bd.ac.kuet.campuscycle.config.ClientConfig.get(name);
        }
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    /** Reads a required secret from the environment / .env; throws when absent (never falls back to source). */
    private static String requireSecret(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            v = bd.ac.kuet.campuscycle.config.ClientConfig.get(name);
        }
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("CampusCycle is missing required configuration: " + name);
        }
        return v.trim();
    }

    /**
     * Creates a {@link PreparedStatement} with a bounded server-side execution timeout.
     * Repositories should use this helper for every query (P-150).
     */
    public static PreparedStatement prepare(Connection conn, String sql) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setQueryTimeout(5);
        return stmt;
    }

    /**
     * Returns true if live PostgreSQL store is reachable.
     * Re-probes on a TTL in both directions (P-057): a success is cached for at most
     * 30s, a failure is retried after 15s — availability is never latched forever.
     */
    public static boolean isAvailable() {
        long now = System.currentTimeMillis();
        Boolean snapshot = available;
        if (snapshot != null) {
            long ttl = snapshot ? SUCCESS_TTL_MS : RETRY_INTERVAL_MS;
            if (now - lastCheckedMs < ttl) {
                return snapshot;
            }
        }
        try (Connection conn = getConnection();
             PreparedStatement stmt = prepare(conn, "SELECT 1")) {
            stmt.execute();
            available = true;
            lastCheckedMs = now;
            lastErrorDetail = "ok";
            return true;
        } catch (Exception e) {
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            // Include which host was attempted — the #1 cause is a wrong-region default.
            lastErrorDetail = detail + " [host=" + resolveHostForLog() + "]";
            LOGGER.log(Level.WARNING, "Supabase PostgreSQL store unreachable; using repository fallback. Reason: " + lastErrorDetail);
            available = false;
            lastCheckedMs = now;
            return false;
        }
    }

    /**
     * Records an observed transport failure so the next {@link #isAvailable()} call
     * re-probes immediately instead of serving a stale cached success. Repositories
     * should call this when a borrowed connection fails (P-057).
     */
    public static synchronized void noteFailure() {
        available = false;
        lastCheckedMs = 0L;
    }

    public static Connection getConnection() throws SQLException {
        PooledEntry entry = acquireEntry();
        return wrap(entry);
    }

    private static PooledEntry acquireEntry() throws SQLException {
        long deadline = System.currentTimeMillis() + (ACQUIRE_TIMEOUT_SEC * 1000L);
        while (System.currentTimeMillis() < deadline) {
            PooledEntry entry = POOL.poll();
            if (entry != null) {
                if (isUsable(entry)) {
                    return entry.touch();
                } else {
                    closeQuietly(entry.physical());
                    decrementTotal();
                }
            } else if (TOTAL_CONNECTIONS.get() < MAX_POOL_SIZE) {
                if (TOTAL_CONNECTIONS.incrementAndGet() <= MAX_POOL_SIZE) {
                    try {
                        Connection physical = createPhysicalConnection();
                        return new PooledEntry(physical, System.currentTimeMillis(), System.currentTimeMillis());
                    } catch (Exception e) {
                        decrementTotal();
                        throw e;
                    }
                } else {
                    decrementTotal();
                }
            } else {
                try {
                    PooledEntry waited = POOL.poll(200, TimeUnit.MILLISECONDS);
                    if (waited != null) {
                        if (isUsable(waited)) {
                            return waited.touch();
                        }
                        closeQuietly(waited.physical());
                        decrementTotal();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted while waiting for database connection", e);
                }
            }
        }
        throw new SQLException("Timed out waiting for an available pooled database connection (pool limit: " + MAX_POOL_SIZE + ")");
    }

    /** Validates a pooled entry without borrowing it. */
    private static boolean isUsable(PooledEntry entry) {
        long now = System.currentTimeMillis();
        boolean expired = (now - entry.createdMs() > MAX_LIFETIME_MS);
        boolean idleTooLong = (now - entry.lastUsedMs() > MAX_IDLE_MS);
        try {
            if (!expired && !entry.physical().isClosed()) {
                if (idleTooLong) {
                    return entry.physical().isValid(1);
                }
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** Decrements the pool counter, floored at zero so accounting drift can never go negative (P-149). */
    private static void decrementTotal() {
        TOTAL_CONNECTIONS.updateAndGet(v -> v <= 0 ? 0 : v - 1);
    }

    /**
     * Resolves the DB host actually used (for error messages only; no connection attempted).
     */
    private static String resolveHostForLog() {
        String explicit = System.getenv("SUPABASE_DB_HOST");
        if (explicit == null || explicit.isBlank()) {
            explicit = bd.ac.kuet.campuscycle.config.ClientConfig.get("SUPABASE_DB_HOST");
        }
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim() + " (explicit)";
        }
        return deriveHostFromSupabaseUrl() + " (derived)";
    }

    /**
     * Derives a region-independent direct host ({@code db.<ref>.supabase.co})
     * from {@code SUPABASE_URL}. The hardcoded pooler default is Tokyo-only;
     * the direct host works for every region.
     */
    private static String deriveHostFromSupabaseUrl() {
        try {
            String url = bd.ac.kuet.campuscycle.config.ClientConfig.supabaseUrl();
            // https://<ref>.supabase.co -> <ref>
            String ref = url.replaceFirst("^https?://", "").split("\\.")[0].trim();
            if (!ref.isBlank() && ref.matches("[a-z0-9]+")) {
                return "db." + ref + ".supabase.co";
            }
        } catch (Exception ignored) {}
        return DEFAULT_HOST;
    }

    private static Connection createPhysicalConnection() throws SQLException {
        // Prefer explicit host; otherwise derive region-independent direct host
        // from SUPABASE_URL (the Tokyo pooler default breaks every other region).
        String explicitHost = env("SUPABASE_DB_HOST", "");
        String host = (explicitHost == null || explicitHost.isBlank())
                ? deriveHostFromSupabaseUrl()
                : explicitHost.trim();
        int port;
        try {
            port = Integer.parseInt(env("SUPABASE_DB_PORT", String.valueOf(DEFAULT_PORT)));
        } catch (NumberFormatException ignored) {
            port = DEFAULT_PORT;
        }
        String db = env("SUPABASE_DB_NAME", DEFAULT_DATABASE);
        String user = requireSecret("SUPABASE_DB_USER");
        String pass = requireSecret("SUPABASE_DB_PASSWORD");

        String url = String.format(
                "jdbc:postgresql://%s:%d/%s?sslmode=require&prepareThreshold=0&loginTimeout=10&connectTimeout=10&socketTimeout=15",
                host, port, db);
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", pass);
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", "require");
        return DriverManager.getConnection(url, props);
    }

    private static Connection wrap(PooledEntry entry) {
        InvocationHandler handler = new InvocationHandler() {
            private volatile boolean closed = false;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if ("close".equals(name)) {
                    if (!closed) {
                        closed = true;
                        release(entry);
                    }
                    return null;
                }
                if ("isClosed".equals(name)) {
                    return closed || entry.physical().isClosed();
                }
                if (closed) {
                    throw new SQLException("Connection is closed.");
                }
                if ("unwrap".equals(name) && args != null && args.length == 1) {
                    Class<?> iface = (Class<?>) args[0];
                    if (iface.isInstance(entry.physical())) {
                        return entry.physical();
                    }
                }
                if ("isWrapperFor".equals(name) && args != null && args.length == 1) {
                    Class<?> iface = (Class<?>) args[0];
                    return iface.isInstance(entry.physical());
                }

                try {
                    return method.invoke(entry.physical(), args);
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    throw ite.getCause() != null ? ite.getCause() : ite;
                }
            }
        };

        return (Connection) Proxy.newProxyInstance(
                DatabaseConnection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler
        );
    }

    private static void release(PooledEntry entry) {
        try {
            if (!entry.physical().isClosed()) {
                if (!entry.physical().getAutoCommit()) {
                    entry.physical().rollback();
                    entry.physical().setAutoCommit(true);
                }
                entry.physical().clearWarnings();
                POOL.offer(entry.touch());
                return;
            }
        } catch (Exception ignored) {}
        closeQuietly(entry.physical());
        decrementTotal();
    }

    private static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignored) {}
        }
    }

    public static synchronized void closeAllPhysical() {
        PooledEntry entry;
        while ((entry = POOL.poll()) != null) {
            closeQuietly(entry.physical());
            decrementTotal();
        }
    }

    public static synchronized void resetAvailability() {
        available = null;
        closeAllPhysical();
    }
}
