package as.sirhephaistos.simplybetter.core.db;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * The shared player registry, {@code core.players}.
 *
 * <p>Not one of simply-better's own tables. It is the player identity that other mods on the same
 * database reference, and simply-better writes it because simply-better sees a player first: it
 * already upserts its own player row on join, at the moment the identity becomes known.
 *
 * <p>Every statement names the table with its schema. An unqualified {@code players} resolves
 * through whatever search path the connecting role happens to have, which can silently be a
 * different table with different columns — the kind of mistake that produces no error until some
 * other mod's foreign key fails, much later and somewhere else.
 *
 * <p>Writing here needs INSERT and UPDATE on that table. Without them every call fails, so the
 * failure is logged at ERROR and says what is missing rather than passing quietly.
 */
public final class CorePlayersRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger("simplybetter-core");

    private final DatabaseManager db;

    public CorePlayersRepository(@NotNull DatabaseManager db) {
        this.db = db;
    }

    /**
     * Records identity and marks the player online.
     *
     * <p>{@code first_seen} is set once and never overwritten: a re-join must not rewrite when the
     * player was first seen. The username is refreshed, because it can change.
     */
    public void onJoin(@NotNull UUID uuid, @NotNull String username) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO core.players (uuid, username, is_online, first_seen, last_seen) " +
                 "VALUES (?, ?, TRUE, NOW(), NOW()) " +
                 "ON CONFLICT (uuid) DO UPDATE SET " +
                 "  username = EXCLUDED.username, " +
                 "  is_online = TRUE, " +
                 "  last_seen = NOW()")) {
            ps.setObject(1, uuid);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Could not record {} in core.players — every mod with a foreign key onto"
                    + " that table will refuse to write rows for this player. If this is a"
                    + " permission error, this role needs INSERT and UPDATE on core.players.",
                    username, e);
        }
    }

    /** Clears the online flag and stamps last_seen. */
    public void onLeave(@NotNull UUID uuid) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE core.players SET is_online = FALSE, last_seen = NOW() WHERE uuid = ?")) {
            ps.setObject(1, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Could not clear the online flag for {} in core.players", uuid, e);
        }
    }

    /**
     * Marks every player offline.
     *
     * <p>Run at startup: a server that stopped without a clean shutdown leaves rows claiming to be
     * online forever, and the web app reads that flag.
     */
    public void markAllOffline() {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE core.players SET is_online = FALSE WHERE is_online = TRUE")) {
            int cleared = ps.executeUpdate();
            if (cleared > 0) {
                LOGGER.info("Cleared {} stale online flag(s) in core.players", cleared);
            }
        } catch (SQLException e) {
            LOGGER.error("Could not clear stale online flags in core.players", e);
        }
    }
}
