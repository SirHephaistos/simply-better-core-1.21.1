package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.core.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DatabaseManager (SimplyBetter Core)
 * Uses ConfigManager to drive PostgreSQL connection settings and thread-pool sizing.
 * Uses HikariCP connection pool to avoid per-query TCP handshake overhead.
 * Connection settings are configured in: <fabricConfig>/simplybetter/sbcore-conf.json
 * Schema: classpath resource "simplybetter/schema.sql"
 * Usage:
 * DatabaseManager db = DatabaseManager.createDefault();
 * db.init(); // once on startup
 * try (Connection c = db.getConnection()) { do your query }
 * db.executor().submit(() -> { run async DB work here });
 * db.shutdown(Duration.ofSeconds(5)); // on shutdown
 */
public final class DatabaseManager {

    // ---- Configuration constants ----
    private static final Logger LOGGER = LoggerFactory.getLogger("simplybetter-core-db");
    private static final String SCHEMA_RESOURCE = "simplybetter/schema.sql";

    // Sentinel table to detect if the schema is already applied.
    private static final String SENTRY_TABLE = "sb_mails"; // change to a guaranteed core table if needed
    // Thread naming for the DB executor
    private static final AtomicInteger THREAD_NUM = new AtomicInteger(0);
    // ---- Instance state ----
    private final Path configDir;        // <fabricConfig>/simplybetter
    private final ConfigManager configManager;
    private volatile boolean initialized = false;
    private ExecutorService executor;
    private HikariDataSource dataSource;

    // ---- Construction ----

    /**
     * Creates a DatabaseManager with the given config directory and ConfigManager.
     * Prefer using {@link #createDefault()} unless you have a special need.
     */
    public DatabaseManager(Path configDir, ConfigManager cfgManager) {
        this.configDir = Objects.requireNonNull(configDir, "configDir");
        this.configManager = Objects.requireNonNull(cfgManager, "configManager");
    }

    /**
     * Creates a DatabaseManager that targets the default Fabric config directory.
     * PostgreSQL connection settings are read from sbcore-conf.json.
     */
    public static DatabaseManager createDefault() {
        Path baseConfig = FabricLoader.getInstance().getConfigDir();
        Path sbDir = baseConfig.resolve("simplybetter");

        // Ensure ConfigManager is set up
        ConfigManager cfg = ConfigManager.createDefault();
        cfg.loadOrCreate();

        return new DatabaseManager(sbDir, cfg);
    }

    // ---- Lifecycle ----

    /**
     * Reads a classpath resource fully into a String (UTF-8).
     */
    @SuppressWarnings("SameParameterValue")
    private static @NotNull String readClasspathResource(String resourcePath) throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = DatabaseManager.class.getClassLoader();

        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found on classpath: " + resourcePath);
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        }
    }

    /**
     * Removes SQL comments and normalizes newlines.
     * - Strips line comments starting with "--" until end-of-line.
     * - Strips block comments across lines.
     * - Normalizes line endings to '\n'.
     */
    private static String stripSqlComments(String sql) {
        if (sql == null || sql.isEmpty()) return "";

        // Normalize line endings first
        String normalized = sql.replace("\r\n", "\n").replace('\r', '\n');

        // Remove block comments: /* ... */
        normalized = normalized.replaceAll("(?s)/\\*.*?\\*/", "");
        // Remove line comments: -- ... (to end of line)
        normalized = normalized.replaceAll("(?m)^\\s*--.*$", "");     // whole line comments
        normalized = normalized.replaceAll("(?m)\\s+--.*$", "");       // trailing comments after SQL

        return normalized;
    }

    /**
     * Splits a schema script into statements by semicolons.
     */
    private static java.util.List<String> splitSqlStatements(String sql) {
        String cleaned = stripSqlComments(sql);
        String[] parts = cleaned.split(";");
        java.util.List<String> out = new java.util.ArrayList<>(parts.length);

        for (String part : parts) {
            String stmt = part.trim();
            if (!stmt.isEmpty()) {
                out.add(stmt);
            }
        }
        return out;
    }

    /**
     * Initializes the database:
     * - Ensures config directory exists.
     * - Creates HikariCP connection pool to PostgreSQL.
     * - If schema is missing, applies it in a single transaction.
     * - Starts the background executor (size from ConfigManager).
     * This method is idempotent: calling it twice throws an IllegalStateException to protect against double init.
     *
     * @throws IllegalStateException if already initialized or if any initialization step fails.
     */
    public synchronized void init() {
        if (initialized) {
            throw new IllegalStateException("DatabaseManager has already been initialized.");
        }
        String masked = configManager.connectionString().replaceAll("://([^:]+):([^@]+)@", "://$1:****@");
        try {
            // Ensure config directory exists
            Files.createDirectories(configDir);

            LOGGER.info("Initializing SimplyBetter DB with PostgreSQL at: {}", masked);

            // Initialize HikariCP connection pool
            initConnectionPool();

            // Check/apply schema using a pooled connection
            try (Connection conn = dataSource.getConnection()) {
                if (!schemaExists(conn)) {
                    LOGGER.info("Schema not found. Applying schema from classpath: {}", SCHEMA_RESOURCE);
                    applySchema(conn); // transactional
                } else {
                    LOGGER.info("Schema already present. Skipping schema application.");
                }
            }

            // Start executor AFTER successful init
            startExecutorFromConfig();

            initialized = true;

            // Log a concise summary
            int logical = Runtime.getRuntime().availableProcessors();
            LOGGER.info("Database initialized: connectionString={}", masked);
            // Also log ConfigManager summary via its own logger (Log4j)
            configManager.logSummary(logical);

        } catch (Exception e) {
            // Clean up pool if init fails partway
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            throw new IllegalStateException("Failed to initialize SimplyBetter database at " + masked, e);
        }
    }

    /**
     * Creates the HikariCP connection pool.
     * Connections are validated automatically — HikariCP handles:
     * - keepaliveTime: pings idle connections every 30s to keep them alive
     * - maxLifetime: rotates connections every 10min so they never go stale
     * - connectionTestQuery: validates each connection before handing it out
     * - minimumIdle: always keeps connections ready in the pool
     */
    private void initConnectionPool() {
        int logical = Runtime.getRuntime().availableProcessors();
        int poolSize = configManager.effectiveThreadCount(logical);

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(configManager.jdbcUrl());
        hikari.setUsername(configManager.dbUser());
        hikari.setPassword(configManager.dbPassword());
        hikari.setPoolName("SimplyBetter-DB-Pool");
        hikari.setMaximumPoolSize(poolSize + 2); // a bit more than executor threads
        hikari.setMinimumIdle(2);                 // always keep 2 connections warm
        hikari.setIdleTimeout(600_000);           // 10 min idle before eviction
        hikari.setMaxLifetime(600_000);           // 10 min — rotate connections automatically
        hikari.setConnectionTimeout(10_000);      // 10s to get a connection from pool
        hikari.setKeepaliveTime(30_000);          // ping every 30s to keep connections alive
        hikari.setConnectionTestQuery("SELECT 1");// validate before handing out

        this.dataSource = new HikariDataSource(hikari);
        LOGGER.info("HikariCP pool initialized: maxPoolSize={}, minIdle=2, maxLifetime=10min, keepalive=30s", poolSize + 2);
    }

    /**
     * Returns a connection from the HikariCP pool (sub-millisecond).
     */
    public Connection getConnection() throws SQLException {
        if (!initialized) {
            throw new SQLException("Database not initialized. Call init() first.");
        }
        return dataSource.getConnection();
    }

    // ---- Internal helpers ----

    /**
     * Provides the dedicated background executor for DB work.
     */
    public ExecutorService executor() {
        ExecutorService ex = this.executor;
        if (!initialized || ex == null || ex.isShutdown()) {
            throw new IllegalStateException("DatabaseManager not initialized or executor is shut down.");
        }
        return ex;
    }

    /**
     * Gracefully shuts down the DB executor and connection pool within the given timeout.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void shutdown(Duration timeout) {
        ExecutorService ex = this.executor;
        if (ex != null) {
            ex.shutdown();
            try {
                if (!ex.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    ex.shutdownNow();
                    ex.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException ie) {
                ex.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        // Close connection pool
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("HikariCP pool closed.");
        }
    }

    /**
     * Drops all SimplyBetter tables and re-initializes the schema.
     */
    public synchronized void reset(Duration shutdownTimeout) {
        shutdown(shutdownTimeout);
        try (Connection conn = DriverManager.getConnection(
                configManager.jdbcUrl(), configManager.dbUser(), configManager.dbPassword());
             Statement st = conn.createStatement()) {
            st.execute("DROP SCHEMA public CASCADE");
            st.execute("CREATE SCHEMA public");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to reset database schema", e);
        }
        initialized = false;
        init();
    }

    /**
     * Checks if the sentinel table exists, honoring the connection's search_path.
     * Uses to_regclass() so the lookup follows whatever schema the role's
     * search_path points at (`public` for standalone deployments, or a custom
     * schema like `sbs` when the unified DB sets `search_path TO sbs, public`).
     */
    private boolean schemaExists(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT to_regclass(?) IS NOT NULL")) {
            ps.setString(1, SENTRY_TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /**
     * Applies the schema from the classpath resource in a single transaction.
     */
    private void applySchema(Connection conn) throws IOException, SQLException {
        String ddl = readClasspathResource(SCHEMA_RESOURCE);
        List<String> statements = splitSqlStatements(ddl);

        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    st.execute(trimmed);
                }
            }
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /**
     * Starts the fixed size executor using sizing from ConfigManager.
     */
    private void startExecutorFromConfig() {
        int logical = Runtime.getRuntime().availableProcessors();
        int size = configManager.effectiveThreadCount(logical);
        this.executor = Executors.newFixedThreadPool(size, r -> {
            Thread t = new Thread(r, "SimplyBetter-DB-" + THREAD_NUM.incrementAndGet());
            t.setDaemon(true); // do not block server shutdown
            t.setUncaughtExceptionHandler((th, ex) ->
                    LOGGER.error("Uncaught exception in DB executor thread", ex));
            return t;
        });
        LOGGER.info("Started DB executor with {} threads (logicalCPUs={})", size, logical);
    }
}
