package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.AfkDTO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AfksCrudManager {
    private final DatabaseManager db;

    public AfksCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create
    /**
     * Map a ResultSet row to AfkDTO.
     * @param rs the ResultSet, positioned at the row to map.
     * @throws SQLException on SQL errors comming from jdbc.
     * @throws IllegalArgumentException if rs is null.
     * @throws IllegalStateException if any non-nullable column is null.
    */
    private static AfkDTO mapAfk(ResultSet rs) throws SQLException {
        if (rs == null) throw new IllegalArgumentException("rs cannot be null");
        if (rs.getString("a_player_uuid") == null)
            throw new IllegalStateException("a_player_uuid cannot be null");
        if (rs.getString("a_since") == null)
            throw  new IllegalStateException("a_since cannot be null");
        @NotNull final String playerUuid = rs.getString("a_player_uuid");
        @NotNull String since = rs.getString("a_since");
        final String message = rs.getString("a_message"); // may be null
        return new AfkDTO(playerUuid, since, message);
    }

    /**
     * Create a new AFK entry for a player.
     * @param playerUuid afk player's uuid.
     * @param since since when the player is afk in string format.
     * @param message optional afk message.
     * @return the created AfkDTO.
     * @throws IllegalStateException if an AFK entry already exists for the player UUID.
     * @throws RuntimeException on SQL errors or if no rows were affected.
     */
    public AfkDTO createAfk(@NotNull String playerUuid, @NotNull String since, @Nullable String message) {
        if (getAfkByPlayerUuid(playerUuid).isPresent()) {
            throw new IllegalStateException("AFK entry already exists for player=" + playerUuid);
        }
        final String sql = """
                INSERT INTO sb_afks (player_uuid, since, message)
                VALUES (?, ?, ?)
                """;
        String id;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setString(2, since);
            if (message == null) ps.setNull(3, Types.VARCHAR);
            else ps.setString(3, message);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new RuntimeException("createAfk failed, no generated keys");
                }
                id = keys.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createAfk failed for player=" + playerUuid, e);
        }
        return getAfkByPlayerUuid(id)
                .orElseThrow(() -> new RuntimeException("createAfk post-fetch missing for player=" + playerUuid));
    }

    // -- Read

    /**
     * Get AFK by player UUID.
     * @param playerUuid the player's UUID.
     * @return an Optional containing the AfkDTO if found, or empty if not found.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<AfkDTO> getAfkByPlayerUuid(@NotNull String playerUuid) {
        final String sql = """
                SELECT
                    a.player_uuid   AS a_player_uuid,
                    a.since AS a_since,
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
     * Get all AFK entries.
     * @return a list of all AfkDTOs.
     * @throws RuntimeException on SQL errors.
     */
    public List<AfkDTO> getAllAfks() {
        final String sql = """
                SELECT
                    a.player_uuid   AS a_player_uuid,
                    a.since AS a_since,
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

    /**
     * Get all AFK entries with pagination.
     * @param limit maximum number of entries to return.
     * @param offset number of entries to skip.
     * @return a list of AfkDTOs.
     * @throws RuntimeException on SQL errors.
     */
    public List<AfkDTO> getAllAfksPaged(int limit, int offset) {
        final String sql = """
                SELECT
                    a.player_uuid   AS a_player_uuid,
                    a.since AS a_since,
                    a.message       AS a_message
                FROM sb_afks a
                ORDER BY a.player_uuid
                LIMIT ? OFFSET ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                final List<AfkDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapAfk(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getAllAfksPaged failed", e);
        }
    }

    /**
     * Check if an AFK entry exists for a player.
     * @param playerUuid the player's UUID.
     * @return true if an AFK entry exists, false otherwise.
     * @throws RuntimeException on SQL errors.
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

    // -- Update

    /**
     * Update both since and message.
     * @param playerUuid the player's UUID.
     * @param since since when the player is afk in string format.
     * @param message optional afk message.
     * @return the updated AfkDTO.
     * @throws RuntimeException on SQL errors or if no rows were affected.
     */
    public AfkDTO updateAfk(@NotNull String playerUuid, @NotNull String since, @Nullable String message) {
        final String sql = """
                UPDATE sb_afks
                SET since = ?, message = ?
                WHERE player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, since);
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
     * Update only message.
     * @param playerUuid the player's UUID.
     * @param message optional afk message.
     * @return the updated AfkDTO.
     * @throws RuntimeException on SQL errors or if no rows were affected.
     */
    public AfkDTO setAfkMessage(@NotNull String playerUuid,@Nullable String message) {
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

    /**
     * Update only since.
     * @param playerUuid the player's UUID.
     * @param since since when the player is afk in string format.
     * @return the updated AfkDTO.
     * @throws RuntimeException on SQL errors or if no rows were affected.
     */
    public AfkDTO setAfkSince(@NotNull String playerUuid, @NotNull String since) {
        final String sql = """
                UPDATE sb_afks
                SET since = ?
                WHERE player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, since);
            ps.setString(2, playerUuid);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setAfkSinceSeconds affected 0 rows for player=" + playerUuid);
        } catch (SQLException e) {
            throw new RuntimeException("setAfkSinceSeconds failed for player=" + playerUuid, e);
        }
        return getAfkByPlayerUuid(playerUuid)
                .orElseThrow(() -> new RuntimeException("setAfkSinceSeconds post-fetch missing for player=" + playerUuid));
    }

    // -- Delete

    /**
     * Delete AFK entry by player UUID.
     * @param playerUuid the player's UUID.
     * @throws IllegalStateException if no AFK entry exists for the player UUID.
     * @throws RuntimeException on SQL errors or if no rows were affected.
     */
    public void deleteAfkByPlayerUuid(@NotNull String playerUuid) {
        if (!existsAfkForPlayer(playerUuid)) {
            throw new IllegalStateException("deleteAfkByPlayerUuid: no AFK entry for player=" + playerUuid);
        }
        final String sql = """
                DELETE FROM sb_afks
                WHERE player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            final int del = ps.executeUpdate();
            if (del == 0) throw new RuntimeException("deleteAfkByPlayerUuid affected 0 rows for player=" + playerUuid);
        } catch (SQLException e) {
            throw new RuntimeException("deleteAfkByPlayerUuid failed for player=" + playerUuid, e);
        }
    }
}
