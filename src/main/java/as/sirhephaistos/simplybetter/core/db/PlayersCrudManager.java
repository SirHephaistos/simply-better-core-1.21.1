package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.PlayerDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD manager for {@link PlayerDTO}.
 * <p>
 * Persists players in table {@code sb_players} with columns:
 * <ul>
 *   <li>uuid TEXT PRIMARY KEY</li>
 *   <li>name TEXT NOT NULL</li>
 *   <li>first_seen TEXT NOT NULL DEFAULT (datetime('now'))</li>
 *   <li>last_seen TEXT NOT NULL DEFAULT (datetime('now'))</li>
 *   <li>playtime_seconds INTEGER NOT NULL DEFAULT 0</li>
 *   <li>can_be_ignored INTEGER NOT NULL DEFAULT 1  <!-- boolean: 1=true, 0=false --></li>
 *   <li>nickname TEXT NULL</li>
 *   <li>last_seen_position_id INTEGER NULL REFERENCES sb_positions(id) ON DELETE SET NULL</li>
 * </ul>
 * All SQLExceptions are wrapped in RuntimeExceptions with context.
 */
public final class PlayersCrudManager {
    private final DatabaseManager db;

    /**
     * Creates a new CRUD manager for players.
     *
     * @param db Database manager providing JDBC connections.
     */
    public PlayersCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    /**
     * Binds a {@link PlayerDTO} to a PreparedStatement in the order used by INSERT:
     * uuid, name, first_seen, last_seen, playtime_seconds, can_be_ignored, nickname, last_seen_position_id.
     */
    private static void bindPlayer(@NotNull PreparedStatement ps, @NotNull PlayerDTO p) throws SQLException {
        ps.setString(1, p.uuid());
        ps.setString(2, p.name());
        ps.setString(3, p.firstSeen());
        ps.setString(4, p.lastSeen());
        ps.setLong(5, p.playtimeSeconds());
        ps.setInt(6, p.canBeIgnored() ? 1 : 0);
        if (p.nickname() == null) {
            ps.setNull(7, Types.VARCHAR);
        } else {
            ps.setString(7, p.nickname());
        }
        if (p.lastSeenPositionId() == null) {
            ps.setNull(8, Types.BIGINT);
        } else {
            ps.setLong(8, p.lastSeenPositionId());
        }
    }

    /**
     * Maps the current row to {@link PlayerDTO}.
     */
    private static PlayerDTO mapPlayer(@NotNull ResultSet rs) throws SQLException {
        final String uuid = rs.getString("uuid");
        final String name = rs.getString("name");
        final String firstSeen = rs.getString("first_seen");
        final String lastSeen = rs.getString("last_seen");
        final long playtimeSeconds = rs.getLong("playtime_seconds");
        final boolean canBeIgnored = rs.getInt("can_be_ignored") != 0;
        final String nickname = rs.getString("nickname");
        final long posId = rs.getLong("last_seen_position_id");
        final Long lastSeenPositionId = rs.wasNull() ? null : posId;
        return new PlayerDTO(uuid, name, firstSeen, lastSeen, playtimeSeconds, canBeIgnored, nickname, lastSeenPositionId);
    }

    /**
     * Inserts or replaces a player row.
     * <p>
     * If the {@code uuid} already exists, this will fail in strict INSERT mode.
     *
     * @param player Input DTO. {@code uuid} must be non-null.
     * @return Persisted {@link PlayerDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public PlayerDTO createPlayer(@NotNull PlayerDTO player) {
        final String sql = """
                INSERT INTO sb_players
                    (uuid, name, first_seen, last_seen, playtime_seconds, can_be_ignored, nickname, last_seen_position_id)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindPlayer(ps, player);
            ps.executeUpdate();
            return player;
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting player uuid=" + player.uuid(), e);
        }
    }

    /**
     * Retrieves a player by UUID.
     *
     * @param uuid Player UUID string.
     * @return Optional containing the found player or empty if none.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<PlayerDTO> getPlayerByUuid(@NotNull String uuid) {
        final String sql = """
                SELECT uuid, name, first_seen, last_seen, playtime_seconds, can_be_ignored, nickname, last_seen_position_id
                FROM sb_players
                WHERE uuid = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapPlayer(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching player uuid=" + uuid, e);
        }
    }

    /**
     * Lists all players ordered by {@code last_seen} descending.
     *
     * @return List of {@link PlayerDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public List<PlayerDTO> getAllPlayers() {
        final String sql = """
                SELECT uuid, name, first_seen, last_seen, playtime_seconds, can_be_ignored, nickname, last_seen_position_id
                FROM sb_players
                ORDER BY last_seen DESC
                """;
        final List<PlayerDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(mapPlayer(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing players", e);
        }
    }

    /**
     * Updates an existing player by UUID.
     *
     * @param uuid   Player UUID.
     * @param player New values to set. {@code uuid} inside the DTO is ignored.
     * @return Optional containing the updated player or empty if the row did not exist.
     * @throws RuntimeException on SQL errors or unexpected empty fetch after update.
     */
    public Optional<PlayerDTO> updatePlayer(@NotNull String uuid, @NotNull PlayerDTO player) {
        if (getPlayerByUuid(uuid).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_players
                SET name = ?, first_seen = ?, last_seen = ?, playtime_seconds = ?, can_be_ignored = ?, nickname = ?, last_seen_position_id = ?
                WHERE uuid = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // reuse binder except uuid position
            ps.setString(1, player.name());
            ps.setString(2, player.firstSeen());
            ps.setString(3, player.lastSeen());
            ps.setLong(4, player.playtimeSeconds());
            ps.setInt(5, player.canBeIgnored() ? 1 : 0);
            if (player.nickname() == null) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, player.nickname());
            }
            if (player.lastSeenPositionId() == null) {
                ps.setNull(7, Types.BIGINT);
            } else {
                ps.setLong(7, player.lastSeenPositionId());
            }
            ps.setString(8, uuid);

            final int updated = ps.executeUpdate();
            if (updated == 0) throw new RuntimeException("No player updated for uuid=" + uuid);

            return getPlayerByUuid(uuid).map(p -> p).or(() -> {
                throw new RuntimeException("Updated player not found for uuid=" + uuid);
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error updating player uuid=" + uuid, e);
        }
    }

    /**
     * Deletes a player by UUID.
     *
     * @param uuid Player UUID.
     * @return Optional containing the pre-delete player or empty if it did not exist.
     * @throws RuntimeException on SQL errors or if delete affects zero rows after existence check.
     */
    public Optional<PlayerDTO> deletePlayerByUuid(@NotNull String uuid) {
        final Optional<PlayerDTO> before = getPlayerByUuid(uuid);
        if (before.isEmpty()) return Optional.empty();

        final String sql = "DELETE FROM sb_players WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            final int affected = ps.executeUpdate();
            if (affected == 0) throw new RuntimeException("No player deleted for uuid=" + uuid);
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting player uuid=" + uuid, e);
        }
    }

    /**
     * Finds players by exact name.
     *
     * @param name Exact player name.
     * @return List of matching players.
     * @throws RuntimeException on SQL errors.
     *                          // TODO: Add case-insensitive or LIKE search variants if needed.
     */
    public List<PlayerDTO> getPlayersByName(@NotNull String name) {
        final String sql = """
                SELECT uuid, name, first_seen, last_seen, playtime_seconds, can_be_ignored, nickname, last_seen_position_id
                FROM sb_players
                WHERE name = ?
                ORDER BY last_seen DESC
                """;
        final List<PlayerDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapPlayer(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching players by name=" + name, e);
        }
    }

    /**
     * Updates {@code last_seen} to the provided timestamp string.
     *
     * @param uuid     Player UUID.
     * @param lastSeen Timestamp string.
     * @return Optional containing the updated player or empty if the row did not exist.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<PlayerDTO> touchLastSeen(@NotNull String uuid, @NotNull String lastSeen) {
        if (getPlayerByUuid(uuid).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_players
                SET last_seen = ?
                WHERE uuid = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lastSeen);
            ps.setString(2, uuid);
            ps.executeUpdate();
            return getPlayerByUuid(uuid);
        } catch (SQLException e) {
            throw new RuntimeException("Error touching last_seen for uuid=" + uuid, e);
        }
    }

    // ------------------------- helpers -------------------------

    /**
     * Increments {@code playtime_seconds} atomically by {@code delta}.
     *
     * @param uuid  Player UUID.
     * @param delta Number of seconds to add. Can be negative.
     * @return Optional containing the updated player or empty if the row did not exist.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<PlayerDTO> addPlaytime(@NotNull String uuid, long delta) {
        if (getPlayerByUuid(uuid).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_players
                SET playtime_seconds = playtime_seconds + ?
                WHERE uuid = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, delta);
            ps.setString(2, uuid);
            ps.executeUpdate();
            return getPlayerByUuid(uuid);
        } catch (SQLException e) {
            throw new RuntimeException("Error incrementing playtime for uuid=" + uuid, e);
        }
    }

    /**
     * Sets or clears {@code last_seen_position_id}.
     *
     * @param uuid       Player UUID.
     * @param positionId Position id to set, or {@code null} to clear.
     * @return Optional containing the updated player or empty if the row did not exist.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<PlayerDTO> setLastSeenPosition(@NotNull String uuid, Long positionId) {
        if (getPlayerByUuid(uuid).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_players
                SET last_seen_position_id = ?
                WHERE uuid = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (positionId == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, positionId);
            }
            ps.setString(2, uuid);
            ps.executeUpdate();
            return getPlayerByUuid(uuid);
        } catch (SQLException e) {
            throw new RuntimeException("Error setting last_seen_position_id for uuid=" + uuid, e);
        }
    }
}
