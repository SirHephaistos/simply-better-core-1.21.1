package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.AccountDTO;
import as.sirhephaistos.simplybetter.library.AfkDTO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <h1><img src="https://docs.godsmg.com/~gitbook/image?url=https%3A%2F%2F602320278-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_hKBWF%252Ficon%252FF3ga5TrIrIMXtWecHo3z%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252017_44_38.png%3Falt%3Dmedia%26token%3D8c3f45e4-ed6f-47ab-a4ab-474d24fa3bb3&width=32&dpr=1&quality=100&sign=2c456f01&sv=2"></img>
 * &nbsp;CRUD manager for {@link AfkDTO}
 * <img src="https://docs-sbs.godsmg.com/~gitbook/image?url=https%3A%2F%2F655127117-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_ofAiW%252Ficon%252F9SRBPTo3OKBsw5DvBwL3%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252000_07_28.png%3Falt%3Dmedia%26token%3D396dda36-5693-4638-b53e-59bf0770f309&width=32&dpr=1&quality=100&sign=55c114e6&sv=2"></img> </h1>
 * <h2>Create Methods</h2>
 * <ul>
 *     <li>{@link #createAfk}:</br>
 *         Create a new AFK entry for a player. And returns the created {@link AfkDTO}.</li>
 * </ul>
 * <h2>Read Methods</h2>
 * <ul>
 *     <li>{@link #getAfkByPlayerUuid}:</br>
 *         Get AFK by player UUID. And returns an {@link Optional} containing {@link AfkDTO} if found, empty otherwise.</li>
 *     <li>{@link #getAllAfks}:</br>
 *         Get all AFK entries. And returns a {@link List} of {@link AfkDTO}.</li>
 *     <li>{@link #getAllAfksPaged}:</br>
 *         Get all AFK entries with pagination. And returns a {@link List} of {@link AfkDTO}.</li>
 *     <li>{@link #existsAfkForPlayer}:</br>
 *         Check if an AFK entry exists for a player. And returns a boolean.</li>
 * </ul>
 * <h2>Update Methods</h2>
 * <ul>
 *     <li>{@link #updateAfk}:</br>
 *         Update both since and message. And returns the updated {@link AfkDTO}.</li>
 *     <li>{@link #setAfkMessage}:</br>
 *         Update only message. And returns the updated {@link AfkDTO}.</li>
 *     <li>{@link #setAfkSince}:</br>
 *         Update only since. And returns the updated {@link AfkDTO}.</li>
 * </ul>
 * <h2>Delete Methods</h2>
 * <ul>
 *     <li>{@link #deleteAfkByPlayerUuid}:</br>
 *         Delete AFK entry by player UUID.</li>
 * </ul>
 *
 *<h3>General Information</h3>
 * @codeBaseStatus Complete
 * @testingStatus AwaitingJUnitTests
 * @author Sirhephaistos
 * @version 1.0
 */
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
