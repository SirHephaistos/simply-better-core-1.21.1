package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.AfkDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_afks.
 * Assumes one AFK row per player (player_uuid is UNIQUE or PRIMARY KEY).
 * Columns assumed:
 * player_uuid TEXT PRIMARY KEY,
 * since_seconds BIGINT NOT NULL,
 * message TEXT NULL
 * // TODO: adjust table/constraints if schema differs.
 */
public final class AfksCrudManager {
    private final DatabaseManager db;

    public AfksCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static AfkDTO mapAfk(ResultSet rs) throws SQLException {
        final String playerUuid = rs.getString("a_player_uuid");
        final long sinceSeconds = rs.getLong("a_since_seconds");
        final String message = rs.getString("a_message"); // may be null
        return new AfkDTO(playerUuid, sinceSeconds, message);
    }

    /**
     * Insert a new AFK record. Fails if the player already has one.
     */
    public AfkDTO createAfk(@NotNull String playerUuid, long sinceSeconds, String message) {
        final String sql = """
                INSERT INTO sb_afks (player_uuid, since_seconds, message)
                VALUES (?, ?, ?)
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setLong(2, sinceSeconds);
            if (message == null) ps.setNull(3, Types.VARCHAR);
            else ps.setString(3, message);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("createAfk failed for player=" + playerUuid, e);
        }
        return getAfkByPlayerUuid(playerUuid)
                .orElseThrow(() -> new RuntimeException("createAfk post-fetch missing for player=" + playerUuid));
    }

    // -- Read

    /**
     * Upsert convenience: create if missing, otherwise update both fields.
     */
    public AfkDTO createOrUpdateAfk(@NotNull String playerUuid, long sinceSeconds, String message) {
        final Optional<AfkDTO> existing = getAfkByPlayerUuid(playerUuid);
        if (existing.isEmpty()) return createAfk(playerUuid, sinceSeconds, message);
        return updateAfk(playerUuid, sinceSeconds, message);
    }

    /**
     * Get AFK by player UUID.
     */
    public Optional<AfkDTO> getAfkByPlayerUuid(@NotNull String playerUuid) {
        final String sql = """
                SELECT
                    a.player_uuid   AS a_player_uuid,
                    a.since_seconds AS a_since_seconds,
                    a.message       AS a_message
                FROM sb_afks a
                WHERE a.player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapAfk(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getAfkByPlayerUuid failed for player=" + playerUuid, e);
        }
    }

    /**
     * List all AFKs.
     */
    public List<AfkDTO> getAllAfks() {
        final String sql = """
                SELECT
                    a.player_uuid   AS a_player_uuid,
                    a.since_seconds AS a_since_seconds,
                    a.message       AS a_message
                FROM sb_afks a
                ORDER BY a.player_uuid
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<AfkDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapAfk(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllAfks failed", e);
        }
    }

    // -- Update

    /**
     * Check if a player is marked AFK.
     */
    public boolean existsAfkForPlayer(@NotNull String playerUuid) {
        final String sql = """
                SELECT 1
                FROM sb_afks a
                WHERE a.player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("existsAfkForPlayer failed for player=" + playerUuid, e);
        }
    }

    /**
     * Update both sinceSeconds and message.
     */
    public AfkDTO updateAfk(@NotNull String playerUuid, long sinceSeconds, String message) {
        final String sql = """
                UPDATE sb_afks
                SET since_seconds = ?, message = ?
                WHERE player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, sinceSeconds);
            if (message == null) ps.setNull(2, Types.VARCHAR);
            else ps.setString(2, message);
            ps.setString(3, playerUuid);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("updateAfk affected 0 rows for player=" + playerUuid);
        } catch (SQLException e) {
            throw new RuntimeException("updateAfk failed for player=" + playerUuid, e);
        }
        return getAfkByPlayerUuid(playerUuid)
                .orElseThrow(() -> new RuntimeException("updateAfk post-fetch missing for player=" + playerUuid));
    }

    /**
     * Update only the message.
     */
    public AfkDTO setAfkMessage(@NotNull String playerUuid, String message) {
        final String sql = """
                UPDATE sb_afks
                SET message = ?
                WHERE player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (message == null) ps.setNull(1, Types.VARCHAR);
            else ps.setString(1, message);
            ps.setString(2, playerUuid);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setAfkMessage affected 0 rows for player=" + playerUuid);
        } catch (SQLException e) {
            throw new RuntimeException("setAfkMessage failed for player=" + playerUuid, e);
        }
        return getAfkByPlayerUuid(playerUuid)
                .orElseThrow(() -> new RuntimeException("setAfkMessage post-fetch missing for player=" + playerUuid));
    }

    // -- Delete

    /**
     * Update only sinceSeconds.
     */
    public AfkDTO setAfkSinceSeconds(@NotNull String playerUuid, long sinceSeconds) {
        final String sql = """
                UPDATE sb_afks
                SET since_seconds = ?
                WHERE player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, sinceSeconds);
            ps.setString(2, playerUuid);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setAfkSinceSeconds affected 0 rows for player=" + playerUuid);
        } catch (SQLException e) {
            throw new RuntimeException("setAfkSinceSeconds failed for player=" + playerUuid, e);
        }
        return getAfkByPlayerUuid(playerUuid)
                .orElseThrow(() -> new RuntimeException("setAfkSinceSeconds post-fetch missing for player=" + playerUuid));
    }

    // -- Mapper

    /**
     * Delete and return previous row if it existed.
     */
    public Optional<AfkDTO> deleteAfkByPlayerUuid(@NotNull String playerUuid) {
        final Optional<AfkDTO> before = getAfkByPlayerUuid(playerUuid);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_afks
                WHERE player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteAfkByPlayerUuid failed for player=" + playerUuid, e);
        }
    }
}
