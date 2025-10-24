package as.sirhephaistos.simplybetter.core.config;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Minimal, robust configuration loader for SimplyBetter core.
 *
 * File path: <config>/simplybetter/sbcore-conf.json
 *
 * Fields:
 *  - threadCount: Integer or null (auto). When set, clamped to [2, 16].
 *  - journalMode: one of WAL, DELETE, TRUNCATE, MEMORY, PERSIST, OFF. (default WAL)
 *  - synchronous: one of OFF, NORMAL, FULL, EXTRA. (default NORMAL)
 *  - foreignKeys: boolean (default true)
 *
 * Behavior:
 *  - Creates file with defaults if missing.
 *  - On load, fills missing/invalid values with defaults and rewrites file (pretty JSON).
 *  - Provides helper to compute effective DB thread pool size given detected logical CPUs.
 */
public final class ConfigManager {
    private static final Logger LOGGER = LogManager.getLogger("SimplyBetter-Core/Config");

    private static final String FILE_NAME = "sbcore-conf.json";

    private static final int MIN_THREADS = 2;
    private static final int MAX_THREADS = 16; // raise if you expect beefy hosts

    private final Path configDir;
    private final Path filePath;

    private final Gson gson;
    private Config config; // in-memory view (never null after load)

    private ConfigManager(Path configDir, Gson gson) {
        this.configDir = Objects.requireNonNull(configDir);
        this.filePath = configDir.resolve(FILE_NAME);
        this.gson = Objects.requireNonNull(gson);
    }

    /** Create with default Fabric config dir: <fabricConfig>/simplybetter */
    public static ConfigManager createDefault() {
        Path base = FabricLoader.getInstance().getConfigDir().resolve("simplybetter");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return new ConfigManager(base, gson);
    }

    /** Ensure directory exists, load or create config, normalize + persist if needed. */
    public void loadOrCreate() {
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create config directory: " + configDir, e);
        }

        boolean needRewrite = false;
        if (Files.notExists(filePath)) {
            this.config = Config.defaults();
            writeConfig(this.config);
            LOGGER.info("Created default config at {}", filePath);
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            Config loaded = gson.fromJson(reader, Config.class);
            if (loaded == null) loaded = Config.defaults();
            // Normalize & validate
            NormalizationResult result = normalize(loaded);
            this.config = result.normalized;
            needRewrite = result.rewrite;
        } catch (IOException | JsonParseException ex) {
            LOGGER.warn("Failed to read/parse config, recreating defaults: {}", ex.toString());
            this.config = Config.defaults();
            needRewrite = true;
        }

        if (needRewrite) {
            writeConfig(this.config);
            LOGGER.info("Rewrote config with normalized values at {}", filePath);
        }
    }

    /** Persist current config (pretty JSON). */
    private void writeConfig(Config cfg) {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            gson.toJson(cfg, writer);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write config file: " + filePath, e);
        }
    }

    /** Compute effective DB thread pool size.
     *  If threadCount is null -> auto heuristic: max(2, floor(logical/2)), clamped to [MIN_THREADS, MAX_THREADS].
     */
    public int effectiveThreadCount(int detectedLogicalProcessors) {
        int logical = Math.max(1, detectedLogicalProcessors);
        Integer cfg = config.threadCount;
        int value;
        if (cfg == null) {
            value = Math.max(MIN_THREADS, logical / 2);
        } else {
            value = cfg;
        }
        if (value < MIN_THREADS) value = MIN_THREADS;
        if (value > MAX_THREADS) value = MAX_THREADS;
        return value;
    }

    public String journalMode() { return config.journalMode; }

    public String synchronous() { return config.synchronous; }

    public boolean foreignKeys() { return config.foreignKeys; }

    public Path filePath() { return filePath; }

    /** Log a compact summary of the effective settings. */
    public void logSummary(int detectedLogicalProcessors) {
        int pool = effectiveThreadCount(detectedLogicalProcessors);
        LOGGER.info("Config: threads={}, logicalCPUs={}, journalMode={}, synchronous={}, foreignKeys={}",
                pool, detectedLogicalProcessors, journalMode(), synchronous(), foreignKeys());
    }

    // ---------------------- normalization & defaults ----------------------

    private NormalizationResult normalize(Config in) {
        boolean rewrite = false;
        Config out = new Config();

        // threadCount: null or int >= 1 (we clamp later in effectiveThreadCount)
        if (in.threadCount != null && in.threadCount <= 0) {
            out.threadCount = null; // invalid -> auto
            rewrite = true;
        } else {
            out.threadCount = in.threadCount; // may be null
        }

        // journalMode: normalize to upper, validate against allowlist
        String jm = nonEmptyUpper(in.journalMode);
        if (!JournalMode.isValid(jm)) {
            out.journalMode = Config.DEFAULT_JOURNAL_MODE;
            rewrite = true;
        } else {
            out.journalMode = jm;
        }

        // synchronous
        String sync = nonEmptyUpper(in.synchronous);
        if (!Synchronous.isValid(sync)) {
            out.synchronous = Config.DEFAULT_SYNCHRONOUS;
            rewrite = true;
        } else {
            out.synchronous = sync;
        }

        // foreign keys
        out.foreignKeys = in.foreignKeys;

        return new NormalizationResult(out, rewrite);
    }

    private static String nonEmptyUpper(String v) {
        if (v == null) return null;
        String t = v.trim();
        if (t.isEmpty()) return null;
        return t.toUpperCase(Locale.ROOT);

    }

    private record NormalizationResult(Config normalized, boolean rewrite) {}

    // ---------------------- types & enums ----------------------

    /** Public shape of JSON config. Keep fields simple for Gson. */
    public static final class Config {
        static final String DEFAULT_JOURNAL_MODE = JournalMode.WAL.name();
        static final String DEFAULT_SYNCHRONOUS = Synchronous.NORMAL.name();

        /** null = auto (heuristic) */
        @SerializedName("threadCount")
        public Integer threadCount = null;

        @SerializedName("journalMode")
        public String journalMode = DEFAULT_JOURNAL_MODE;

        @SerializedName("synchronous")
        public String synchronous = DEFAULT_SYNCHRONOUS;

        @SerializedName("foreignKeys")
        public boolean foreignKeys = true;

        public static Config defaults() { return new Config(); }
    }

    public enum JournalMode {
        WAL, DELETE, TRUNCATE, MEMORY, PERSIST, OFF;
        public static boolean isValid(String s) {
            if (s == null) return false;
            try { JournalMode.valueOf(s); return true; } catch (IllegalArgumentException ex) { return false; }
        }
    }

    public enum Synchronous {
        OFF, NORMAL, FULL, EXTRA;
        public static boolean isValid(String s) {
            if (s == null) return false;
            try { Synchronous.valueOf(s); return true; } catch (IllegalArgumentException ex) { return false; }
        }
    }
}
