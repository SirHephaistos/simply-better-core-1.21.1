package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.BackLocationDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD manager for {@link BackLocationDTO}.
 * <p>
 * Persists rows in table {@code sb_back_locations} with columns:
 * <ul>
 *   <li>player_uuid TEXT PRIMARY KEY REFERENCES sb_players(uuid) ON DELETE CASCADE</li>
 *   <li>updated_at TEXT NOT NULL DEFAULT (datetime('now'))</li>
 *   <li>previous_position_id INTEGER NOT NULL REFERENCES sb_positions(id) ON DELETE CASCADE</li>
 *   <li>current_position_id  INTEGER NOT NULL REFERENCES sb_positions(id) ON DELETE CASCADE</li>
 * </ul>
 * All SQLExceptions are wrapped into RuntimeExceptions with context.
 */
public final class BackLocationsCrudManager {
    private final DatabaseManager db;

    /**
     * Creates a new CRUD manager for back locations.
     *
     * @param db Database manager providing JDBC connections.
     */
    public BackLocationsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    private static long requireId(Long id, String field) {
        if (id == null) throw new IllegalArgumentException(field + " must not be null");
        return id;
    }

    private static BackLocationDTO map(@NotNull ResultSet rs) throws SQLException {
        final String playerUuid = rs.getString("player_uuid");
        final String updatedAt = rs.getString("updated_at");
        final long prev = rs.getLong("previous_position_id");
        final long curr = rs.getLong("current_position_id");
        return new BackLocationDTO(playerUuid, updatedAt, prev, curr);
    }

    /**
     * Inserts a new back-location row for a player.
     * If {@code updatedAt} is null the database default current timestamp is used.
     *
     * @param b Input DTO. {@code playerUuid}, {@code previousPositionId}, and {@code currentPositionId} must be non-null.
     * @return Persisted {@link BackLocationDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public BackLocationDTO createBackLocation(@NotNull BackLocationDTO b) {
        final String sql = """
                INSERT INTO sb_back_locations (player_uuid, updated_at, previous_position_id, current_position_id)
                VALUES (?, COALESCE(?, datetime('now')), ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, b.playerUuid());
            if (b.updatedAt() == null) ps.setNull(2, Types.VARCHAR);
            else ps.setString(2, b.updatedAt());
            ps.setLong(3, requireId(b.previousPositionId(), "previousPositionId"));
            ps.setLong(4, requireId(b.currentPositionId(), "currentPositionId"));
            ps.executeUpdate();
            // If updatedAt was null, re-fetch to return the actual stored timestamp
            return getBackLocationByPlayerUuid(b.playerUuid()).orElseGet(() -> b);
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting back location for player=" + b.playerUuid(), e);
        }
    }

    /**
     * Retrieves a back-location by player UUID.
     *
     * @param playerUuid Player UUID.
     * @return Optional containing the found {@link BackLocationDTO}, empty if none.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<BackLocationDTO> getBackLocationByPlayerUuid(@NotNull String playerUuid) {
        final String sql = """
                SELECT player_uuid, updated_at, previous_position_id, current_position_id
                FROM sb_back_locations
                WHERE player_uuid = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching back location for player=" + playerUuid, e);
        }
    }

    /**
     * Lists all back-locations ordered by {@code updated_at} descending.
     *
     * @return List of {@link BackLocationDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public List<BackLocationDTO> getAllBackLocations() {
        final String sql = """
                SELECT player_uuid, updated_at, previous_position_id, current_position_id
                FROM sb_back_locations
                ORDER BY updated_at DESC
                """;
        final List<BackLocationDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing back locations", e);
        }
    }

    /**
     * Updates an existing back-location row by player UUID.
     *
     * @param playerUuid Player UUID (primary key).
     * @param b          New values to set. {@code playerUuid} inside DTO is ignored.
     * @return Optional containing the updated {@link BackLocationDTO}, empty if the row did not exist.
     * @throws RuntimeException on SQL errors or unexpected empty fetch after update.
     */
    public Optional<BackLocationDTO> updateBackLocation(@NotNull String playerUuid, @NotNull BackLocationDTO b) {
        if (getBackLocationByPlayerUuid(playerUuid).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_back_locations
                SET updated_at = ?, previous_position_id = ?, current_position_id = ?
                WHERE player_uuid = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, b.updatedAt());
            ps.setLong(2, requireId(b.previousPositionId(), "previousPositionId"));
            ps.setLong(3, requireId(b.currentPositionId(), "currentPositionId"));
            ps.setString(4, playerUuid);
            final int updated = ps.executeUpdate();
            if (updated == 0) throw new RuntimeException("No back location updated for player=" + playerUuid);

            return getBackLocationByPlayerUuid(playerUuid).map(x -> x).or(() -> {
                throw new RuntimeException("Updated back location not found for player=" + playerUuid);
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error updating back location for player=" + playerUuid, e);
        }
    }

    // ------------------------- helpers -------------------------

    /**
     * Convenience method to set both previous and current position ids.
     *
     * @param playerUuid         Player UUID.
     * @param previousPositionId {@code sb_positions.id} to store as previous.
     * @param currentPositionId  {@code sb_positions.id} to store as current.
     * @param updatedAt          Timestamp string to persist.
     * @return Optional containing the updated row or empty if the row did not exist.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<BackLocationDTO> setPositions(@NotNull String playerUuid,
                                                  long previousPositionId,
                                                  long currentPositionId,
                                                  @NotNull String updatedAt) {
        if (getBackLocationByPlayerUuid(playerUuid).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_back_locations
                SET updated_at = ?, previous_position_id = ?, current_position_id = ?
                WHERE player_uuid = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, updatedAt);
            ps.setLong(2, previousPositionId);
            ps.setLong(3, currentPositionId);
            ps.setString(4, playerUuid);
            ps.executeUpdate();
            return getBackLocationByPlayerUuid(playerUuid);
        } catch (SQLException e) {
            throw new RuntimeException("Error setting positions for player=" + playerUuid, e);
        }
    }

    /**
     * Deletes a back-location row by player UUID.
     *
     * @param playerUuid Player UUID.
     * @return Optional containing the pre-delete row or empty if none existed.
     * @throws RuntimeException on SQL errors or if delete affects zero rows after existence check.
     */
    public Optional<BackLocationDTO> deleteBackLocationByPlayerUuid(@NotNull String playerUuid) {
        final Optional<BackLocationDTO> before = getBackLocationByPlayerUuid(playerUuid);
        if (before.isEmpty()) return Optional.empty();

        final String sql = "DELETE FROM sb_back_locations WHERE player_uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            final int affected = ps.executeUpdate();
            if (affected == 0) throw new RuntimeException("No back location deleted for player=" + playerUuid);
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting back location for player=" + playerUuid, e);
        }
    }
}
