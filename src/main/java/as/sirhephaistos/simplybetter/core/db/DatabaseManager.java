package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.core.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;

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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DatabaseManager (SimplyBetter Core)
 * Now uses ConfigManager to drive PRAGMAs and thread-pool sizing.
 *
 * Default paths:
 *  - Config dir: <fabricConfig>/simplybetter/
 *  - DB file:    <fabricConfig>/simplybetter/simplybetter.db
 *  - Schema:     classpath resource "simplybetter/schema.sql"
 *
 * Usage:
 *   DatabaseManager db = DatabaseManager.createDefault();
 *   db.init(); // once on startup
 *   try (Connection c = db.getConnection()) { do your query }
        *   db.executor().submit(() -> { run async DB work here *});
        *   db.shutdown(Duration.ofSeconds(5)); // on shutdown
        */
public final class DatabaseManager {

    // ---- Configuration constants ----
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final String DB_FILE_NAME = "simplybetter.db";
    private static final String SCHEMA_RESOURCE = "simplybetter/schema.sql";

    // Sentinel table to detect if the schema is already applied.
    private static final String SENTRY_TABLE = "sb_mails"; // change to a guaranteed core table if needed

    // ---- Instance state ----
    private final Path configDir;        // <fabricConfig>/simplybetter
    private final Path dbPathAbs;        // <fabricConfig>/simplybetter/simplybetter.db
    private final ConfigManager configManager;
    private volatile boolean initialized = false;
    private ExecutorService executor;

    // Thread naming for the DB executor
    private static final AtomicInteger THREAD_NUM = new AtomicInteger(0);

    // ---- Construction ----

    /**
     * Creates a DatabaseManager that targets the default Fabric config directory.
     * DB will reside at: <config>/simplybetter/simplybetter.db
     * If an old DB exists at <config>/simplybetter.db, it is migrated (moved) once.
     */
    public static DatabaseManager createDefault() {
        Path baseConfig = FabricLoader.getInstance().getConfigDir();
        Path sbDir = baseConfig.resolve("simplybetter");

        // Ensure ConfigManager is set up
        ConfigManager cfg = ConfigManager.createDefault();
        cfg.loadOrCreate();

        // Compute target DB path
        Path newDbPath = sbDir.resolve(DB_FILE_NAME);

        // Optional one-time migration from legacy path: <config>/simplybetter.db
        Path legacyDbPath = baseConfig.resolve(DB_FILE_NAME);
        if (Files.exists(legacyDbPath) && !Files.exists(newDbPath)) {
            try {
                Files.createDirectories(sbDir);
                Files.move(legacyDbPath, newDbPath);
                LOGGER.info(() -> "Moved legacy DB to: " + newDbPath);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to migrate legacy DB from " + legacyDbPath + " to " + newDbPath, e);
            }
        }

        return new DatabaseManager(sbDir, newDbPath, cfg);
    }

    /**
     * Creates a DatabaseManager for a specific absolute DB path inside a given config directory.
     * Prefer using {@link #createDefault()} unless you have a special need.
     */
    public DatabaseManager(Path configDir, Path absoluteDbPath, ConfigManager cfgManager) {
        this.configDir = Objects.requireNonNull(configDir, "configDir");
        this.dbPathAbs = Objects.requireNonNull(absoluteDbPath, "absoluteDbPath");
        this.configManager = Objects.requireNonNull(cfgManager, "configManager");
    }

    // ---- Lifecycle ----

    /**
     * Initializes the database:
     * - Ensures parent directory exists.
     * - Opens a connection to create the file if missing.
     * - If schema is missing, applies it in a single transaction.
     * - Applies SQLite PRAGMAs using a separate fresh connection (from ConfigManager).
     * - Starts the background executor (size from ConfigManager).
     * This method is idempotent: calling it twice throws an IllegalStateException to protect against double init.
     * @throws IllegalStateException if already initialized or if any initialization step fails.
     */
    public synchronized void init() {
        if (initialized) {
            throw new IllegalStateException("DatabaseManager has already been initialized.");
        }
        try {
            // Ensure parent directories exist
            Files.createDirectories(configDir);
            Path parent = dbPathAbs.getParent();
            if (parent != null) Files.createDirectories(parent);

            final String jdbcUrl = jdbcUrl();
            LOGGER.info(() -> "Initializing SimplyBetter DB at: " + dbPathAbs);

            // Open a short-lived connection to ensure file exists and to check/apply schema
            try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                if (!schemaExists(conn)) {
                    LOGGER.info("Schema not found. Applying schema from classpath: " + SCHEMA_RESOURCE);
                    applySchema(conn); // transactional
                } else {
                    LOGGER.info("Schema already present. Skipping schema application.");
                }
            }

            // Apply PRAGMAs in a separate fresh connection (respecting config)
            String effectiveJournal = applyPragmasFromConfig();

            // Start executor AFTER successful init
            startExecutorFromConfig();

            initialized = true;

            // Log a concise summary
            int logical = Runtime.getRuntime().availableProcessors();
            LOGGER.info(() -> "Database initialized: path=" + dbPathAbs +
                    ", journalMode=" + effectiveJournal +
                    ", synchronous=" + configManager.synchronous() +
                    ", foreignKeys=" + configManager.foreignKeys());
            // Also log ConfigManager summary via its own logger (Log4j)
            configManager.logSummary(logical);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize SimplyBetter database at " + dbPathAbs, e);
        }
    }

    /**
     * Opens a new short-lived connection to the SQLite database.
     * Always enable foreign key enforcement and a reasonable busy timeout.
     */
    public Connection getConnection() throws SQLException {
        if (!initialized) {
            throw new SQLException("Database not initialized. Call init() first.");
        }
        Connection conn = DriverManager.getConnection(jdbcUrl());
        try (Statement s = conn.createStatement()) {
            // Keep per-connection safety knobs
            s.execute("PRAGMA foreign_keys = ON;");
            s.execute("PRAGMA busy_timeout = 5000;");
        }
        return conn;
    }

    /** Provides the dedicated background executor for DB work. */
    public ExecutorService executor() {
        ExecutorService ex = this.executor;
        if (!initialized || ex == null || ex.isShutdown()) {
            throw new IllegalStateException("DatabaseManager not initialized or executor is shut down.");
        }
        return ex;
    }

    /** Gracefully shuts down the DB executor within the given timeout. */
    public void shutdown(Duration timeout) {
        ExecutorService ex = this.executor;
        if (ex == null) return;

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

    /** Deletes the DB file and re-initializes it with the current schema. */
    public synchronized void reset(Duration shutdownTimeout) {
        shutdown(shutdownTimeout);
        try {
            Files.deleteIfExists(dbPathAbs);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete DB file at " + dbPathAbs, e);
        }
        initialized = false;
        init();
    }

    // ---- Internal helpers ----

    private String jdbcUrl() {
        return "jdbc:sqlite:" + dbPathAbs.toAbsolutePath();
    }

    /** Checks if the sentinel table exists in sqlite_master. */
    private boolean schemaExists(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1")) {
            ps.setString(1, SENTRY_TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Applies the schema from the classpath resource in a single transaction. */
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

    /** Applies PRAGMAs from ConfigManager using a fresh connection. */
    private String applyPragmasFromConfig() {
        String requestedJournal = configManager.journalMode();
        String sync = configManager.synchronous();
        boolean fk = configManager.foreignKeys();
        String effectiveJournal = requestedJournal;

        try (Connection c = DriverManager.getConnection(jdbcUrl());
             Statement s = c.createStatement()) {
            // journal_mode returns the effective mode in a one-row result set
            try (ResultSet rs = s.executeQuery("PRAGMA journal_mode = " + requestedJournal + ";")) {
                if (rs.next()) {
                    effectiveJournal = rs.getString(1);
                }
            }
            s.execute("PRAGMA synchronous = " + sync + ";");
            s.execute("PRAGMA foreign_keys = " + (fk ? "ON" : "OFF") + ";");
            s.execute("PRAGMA busy_timeout = 5000;");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to apply SQLite PRAGMAs from config.", e);
        }
        if (!requestedJournal.equalsIgnoreCase(effectiveJournal)) {
            LOGGER.warning("Requested journal_mode=" + requestedJournal +
                    ", but SQLite applied=" + effectiveJournal);
        }
        return effectiveJournal;
    }

    /** Starts the fixed size executor using sizing from ConfigManager. */
    private void startExecutorFromConfig() {
        int logical = Runtime.getRuntime().availableProcessors();
        int size = configManager.effectiveThreadCount(logical);
        this.executor = Executors.newFixedThreadPool(size, r -> {
            Thread t = new Thread(r, "SimplyBetter-DB-" + THREAD_NUM.incrementAndGet());
            t.setDaemon(true); // do not block server shutdown
            t.setUncaughtExceptionHandler((th, ex) ->
                    LOGGER.log(Level.SEVERE, "Uncaught exception in DB executor thread", ex));
            return t;
        });
        LOGGER.info(() -> "Started DB executor with " + size + " threads (logicalCPUs=" + logical + ")");
    }

    /** Reads a classpath resource fully into a String (UTF-8). */
    private static String readClasspathResource(String resourcePath) throws IOException {
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
     * - Strips block comments /* ... *\/ across lines.
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
     * Preconditions for using this simple splitter:
     * - Each SQL statement ends with a single ';'.
     * - No semicolons appear inside string literals or triggers.
     * - No BEGIN ... END blocks with internal ';'.
     *
     * Behavior:
     * - Removes comments first.
     * - Splits on ';'.
     * - Trims each chunk.
     * - Discards empty chunks.
     * - If the file does not end with ';', the last non-empty chunk is kept as a statement.
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
}
