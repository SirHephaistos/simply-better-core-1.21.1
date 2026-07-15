package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.proxymc.api.DbHandle;
import as.sirhephaistos.proxymc.api.DbRole;
import as.sirhephaistos.proxymc.api.ProxyDb;
import as.sirhephaistos.simplybetter.core.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DatabaseManager (SimplyBetter Core)
 *
 * Since the proxymc migration this class no longer owns a connection pool or
 * applies schema DDL: connections come from the proxymc gateway's SBS role
 * handle (mTLS to the unified DB, search_path -> sbs, core, public), and DDL
 * is owned by the infrastructure side. The public API used by the CRUD
 * managers and the sibling mods (getConnection()/executor()/shutdown()) is
 * unchanged.
 *
 * ConfigManager is still used for DB executor thread-pool sizing
 * (sbcore-conf.json "threadCount"); its "connectionString" field is ignored.
 */
public final class DatabaseManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("simplybetter-core-db");
    private static final AtomicInteger THREAD_NUM = new AtomicInteger(0);

    private final Path configDir;        // <fabricConfig>/simplybetter
    private final ConfigManager configManager;
    private volatile boolean initialized = false;
    private ExecutorService executor;
    private DbHandle handle;

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
     */
    public static DatabaseManager createDefault() {
        Path baseConfig = FabricLoader.getInstance().getConfigDir();
        Path sbDir = baseConfig.resolve("simplybetter");

        ConfigManager cfg = ConfigManager.createDefault();
        cfg.loadOrCreate();

        return new DatabaseManager(sbDir, cfg);
    }

    /**
     * Initializes the database access:
     * - Claims the SBS role handle from the proxymc gateway.
     * - Validates connectivity with a test query.
     * - Starts the background executor (size from ConfigManager).
     * This method is idempotent-protected: calling it twice throws.
     *
     * @throws IllegalStateException if already initialized or the gateway is unreachable.
     */
    public synchronized void init() {
        if (initialized) {
            throw new IllegalStateException("DatabaseManager has already been initialized.");
        }
        try {
            Files.createDirectories(configDir);

            LOGGER.info("Claiming SBS database handle from proxymc gateway...");
            handle = ProxyDb.claim(DbRole.SBS, "simplybetter-core");

            // Validate connectivity once at boot so misconfiguration surfaces here.
            try (Connection conn = handle.getConnection();
                 Statement st = conn.createStatement()) {
                st.execute("SELECT 1");
            }

            startExecutorFromConfig();
            initialized = true;

            int logical = Runtime.getRuntime().availableProcessors();
            LOGGER.info("Database initialized via proxymc (role sbs_app).");
            configManager.logSummary(logical);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize SimplyBetter database via proxymc gateway", e);
        }
    }

    /**
     * Returns a pooled connection from the proxymc SBS handle.
     */
    public Connection getConnection() throws SQLException {
        DbHandle h = this.handle;
        if (!initialized || h == null) {
            throw new SQLException("Database not initialized. Call init() first.");
        }
        return h.getConnection();
    }

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
     * Gracefully shuts down the DB executor within the given timeout.
     * The connection pool belongs to proxymc and is closed by the gateway
     * itself at SERVER_STOPPED (after this mod's stop handlers).
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
