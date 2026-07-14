package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.PlayerDTO;
import as.sirhephaistos.simplybetter.library.PositionDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.List;
import java.util.Optional;

/**
 * <h1><img src="https://docs.godsmg.com/~gitbook/image?url=https%3A%2F%2F602320278-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_hKBWF%252Ficon%252FF3ga5TrIrIMXtWecHo3z%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252017_44_38.png%3Falt%3Dmedia%26token%3D8c3f45e4-ed6f-47ab-a4ab-474d24fa3bb3&width=32&dpr=1&quality=100&sign=2c456f01&sv=2"></img>
 * &nbsp;CRUD manager for {@link PlayerDTO}
 * <img src="https://docs-sbs.godsmg.com/~gitbook/image?url=https%3A%2F%2F655127117-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_ofAiW%252Ficon%252F9SRBPTo3OKBsw5DvBwL3%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252000_07_28.png%3Falt%3Dmedia%26token%3D396dda36-5693-4638-b53e-59bf0770f309&width=32&dpr=1&quality=100&sign=55c114e6&sv=2"></img> </h1>
 * <h2>Create Methods</h2>
 * <ul>
 *     <li> {@link #createPlayer(String, String, String, String, long, boolean, String, Long)}:</br>
 *         Creates a new player with the provided details. Returns the created {@link PlayerDTO}. </li>
 *     <li> {@link #createPlayer(PlayerDTO)}:</br>
 *         Creates a new player using a {@link PlayerDTO} object. Returns the created {@link PlayerDTO}. </li>
 * </ul>
 * <h2>Read Methods</h2>
 * <ul>
 *     <li>{@link #getPlayerByUuid(String)}:</br>
 *         Retrieves a player by their UUID. Returns an {@link Optional} containing the {@link PlayerDTO} if found, or empty if not found. </li>
 *     <li>{@link #getPlayerByName(String)}:</br>
 *         Retrieves a player by their name. Returns an {@link Optional} containing the {@link PlayerDTO} if found, or empty if not found. </li>
 *     <li>{@link #getAllPlayers()}:</br>
 *         Retrieves all players. Returns a {@link List} of {@link PlayerDTO} objects. </li>
 *     <li>{@link #getAllPlayersPaged(int, int)}:</br>
 *         Retrieves players in a paginated manner. Accepts limit and offset parameters.
 *         Returns a {@link List} of {@link PlayerDTO} objects. </li>
 * </ul>
 * <h2>Update Methods</h2>
 * <ul>
 *     <li>{@link #updatePlayer(String, PlayerDTO)}:</br>
 *         Updates an existing player's details identified by their UUID.
 *         Returns an {@link Optional} containing the updated {@link PlayerDTO} if the update was successful, or empty if the player was not found. </li>
 * </ul>
 * <h2>Delete Methods</h2>
 * <ul>
 *     <li>{@link #deletePlayerByUuid(String)}:</br>
 *         Deletes a player by their UUID. </li>
 * </ul>
 *
 *<h3>General Information</h3>
 * @codeBaseStatus Complete
 * @testingStatus AwaitingJUnitTests
 * @author Sirhephaistos
 * @version 1.0
 */
public final class PlayersCrudManager {
    private final DatabaseManager db;
    public PlayersCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }


    /**
     * Private helper to map a {@link PlayerDTO} from a {@link ResultSet}.
     * @param rs the {@link ResultSet}, positioned at the desired row to map.
     * @return the mapped {@link PlayerDTO}
     * @throws SQLException on SQL errors coming from JDBC.
     * @throws IllegalArgumentException if rs is null.
     * @throws IllegalStateException if any non-nullable column is null or unexpected SQL NULLs are encountered.
     */
    private PlayerDTO mapPlayer(ResultSet rs) throws SQLException {
        if (rs == null) throw new IllegalArgumentException("rs is null");
        @NotNull final String uuid = rs.getString("uuid");
        if (uuid == null) throw new IllegalStateException("uuid is null");
        @NotNull final String name = rs.getString("name");
        if (name == null) throw new IllegalStateException("name is null");
        @NotNull final String firstSeen = rs.getString("first_seen");
        if (firstSeen == null) throw new IllegalStateException("first_seen is null");
        @NotNull final String lastSeen = rs.getString("last_seen");
        if (lastSeen == null) throw new IllegalStateException("last_seen is null");
        final int playtimeSeconds = rs.getInt("playtime_seconds");
        if (rs.wasNull()) throw new IllegalStateException("playtime_seconds is null");
        final boolean canBeIgnored = rs.getInt("can_be_ignored") == 1;
        final String nickname = rs.getString("nickname");
        final long lastSeenPositionId = rs.getLong("last_seen_position_id");
        final Long lastSeenPositionIdNullable = rs.wasNull() ? null : lastSeenPositionId;
        return new PlayerDTO(uuid, name, firstSeen, lastSeen, playtimeSeconds, canBeIgnored, nickname, lastSeenPositionIdNullable);
    }

    // -- Create

    /**
     * Create a new player in databse for given {@code playerUUID} and {@code playerName}.
     * @param uuid {@code playerUUID}
     * @param name {@code playerName}
     * @param firstSeen timestamp string for first seen
     * @param lastSeen timestamp string for last seen
     * @param playtimeSeconds initial playtime in seconds
     * @param canBeIgnored initial canBeIgnored flag
     * @param nickname optional nickname
     * @param lastSeenPositionId optional last seen position id
     * @return the created {@link PlayerDTO}
     * @throws IllegalStateException if the created player cannot be retrieved
     * @throws RuntimeException on SQL errors during creation
     */
    public PlayerDTO createPlayer(@NotNull String uuid, @NotNull String name, @NotNull String firstSeen,
                                  @NotNull String lastSeen, long playtimeSeconds, boolean canBeIgnored,
                                  String nickname, Long lastSeenPositionId){
        final String sql = """
                INSERT INTO sb_players (uuid, name, first_seen, last_seen, playtime_seconds, can_be_ignored, nickname, last_seen_position_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING *
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.setString(3, firstSeen);
            ps.setString(4, lastSeen);
            ps.setLong(5, playtimeSeconds);
            ps.setInt(6, canBeIgnored ? 1 : 0);
            if (nickname != null) {
                ps.setString(7, nickname);
            } else {
                ps.setNull(7, Types.VARCHAR);
            }
            if (lastSeenPositionId != null) {
                ps.setLong(8, lastSeenPositionId);
            } else {
                ps.setNull(8, Types.BIGINT);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("INSERT RETURNING returned no rows for uuid " + uuid);
                return mapPlayer(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create player", e);
        }
    }

    /**
     * Create a new player in database from a {@link PlayerDTO}.
     * @param playerDTO the non-null {@link PlayerDTO} to create.
     * @return the created {@link PlayerDTO}.
     * @throws SQLException on SQL errors during creation.
     */
    public PlayerDTO createPlayer(PlayerDTO playerDTO) throws SQLException {
        return createPlayer(playerDTO.uuid(),
                playerDTO.name(),
                playerDTO.firstSeen(),
                playerDTO.lastSeen(),
                playerDTO.playtimeSeconds(),
                playerDTO.canBeIgnored(),
                playerDTO.nickname(),
                playerDTO.lastSeenPositionId());
    }

    /**
     * Upsert a player row: insert or update on primary key (uuid) conflict.
     * All columns are written from the provided {@link PlayerDTO}. On conflict, every column is updated from
     * the "excluded" values. The method then reloads and returns the persisted row.
     *
     * @param p the non-null {@link PlayerDTO} to insert or update.
     * @return the persisted {@link PlayerDTO} refreshed from the database.
     * @throws RuntimeException on SQL errors or if the refreshed row cannot be retrieved.
     */
    public PlayerDTO upsertPlayer(@org.jetbrains.annotations.NotNull PlayerDTO p) {
        final String sql = """
            INSERT INTO sb_players (uuid, name, first_seen, last_seen, playtime_seconds, can_be_ignored, nickname, last_seen_position_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
              name = excluded.name,
              first_seen = excluded.first_seen,
              last_seen = excluded.last_seen,
              playtime_seconds = excluded.playtime_seconds,
              can_be_ignored = excluded.can_be_ignored,
              nickname = excluded.nickname,
              last_seen_position_id = excluded.last_seen_position_id
            RETURNING *
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, p.uuid());
            stmt.setString(2, p.name());
            stmt.setString(3, p.firstSeen());
            stmt.setString(4, p.lastSeen());
            stmt.setLong(5, p.playtimeSeconds());
            stmt.setInt(6, p.canBeIgnored() ? 1 : 0);
            if (p.nickname() != null) {
                stmt.setString(7, p.nickname());
            } else {
                stmt.setNull(7, Types.VARCHAR);
            }
            if (p.lastSeenPositionId() != null) {
                stmt.setLong(8, p.lastSeenPositionId());
            } else {
                stmt.setNull(8, Types.BIGINT);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("UPSERT RETURNING returned no rows for uuid " + p.uuid());
                return mapPlayer(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert player " + p.uuid(), e);
        }
    }
    // -- Read
    public Optional<PlayerDTO> getPlayerByUuid(@NotNull String uuid) {
        final String sql = """
                Select uuid, name, first_seen, last_seen, playtime_seconds, can_be_ignored, nickname, last_seen_position_id
                FROM sb_players
                WHERE uuid = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if(!rs.next()) return Optional.empty();
                return Optional.of(mapPlayer(rs));
            }
        }catch (SQLException e) {
            throw new RuntimeException("Failed to get player by uuid " + uuid, e);
        }
    }

    /**
     * Get a player by their name. Returns the first match for the given name.
     * Please note that player names are not unique and may change over time in Minecraft.
     * @param name The name of the player.
     * @return An {@link Optional} containing a {@link PlayerDTO} if found, or empty if no player is found.
     */
    public Optional<PlayerDTO> getPlayerByName(@NotNull String name) {
        final String sql = """
                Select uuid, name, first_seen, last_seen, playtime_seconds, can_be_ignored, nickname, last_seen_position_id
                FROM sb_players
                WHERE name = ?
                LIMIT 1
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if(!rs.next()) return Optional.empty();
                return Optional.of(mapPlayer(rs));
            }
        }catch (SQLException e) {
            throw new RuntimeException("Failed to get player by name " + name, e);
        }
    }

    public List<PlayerDTO> getAllPlayers() {
        final String sql = """
                Select uuid, name, first_seen, last_seen, playtime_seconds, can_be_ignored, nickname, last_seen_position_id
                FROM sb_players
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            final List<PlayerDTO> players = new java.util.ArrayList<>();
            while (rs.next()) {
                players.add(mapPlayer(rs));
            }
            return players;
        }catch (SQLException e) {
            throw new RuntimeException("Failed to get all players", e);
        }
    }

    public List<PlayerDTO> getAllPlayersPaged(int limit, int offset) {
        final String sql = """
                Select uuid, name, first_seen, last_seen, playtime_seconds, can_be_ignored, nickname, last_seen_position_id
                FROM sb_players
                LIMIT ? OFFSET ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                final List<PlayerDTO> players = new java.util.ArrayList<>();
                while (rs.next()) {
                    players.add(mapPlayer(rs));
                }
                return players;
            }
        }catch (SQLException e) {
            throw new RuntimeException("Failed to get all players paged", e);
        }
    }

// -- Queries

    /**
     * LIKE search by name with pagination.
     * Example pattern: "%steve%".
     * @param pattern SQL LIKE pattern to match against name.
     * @param limit maximum number of rows to return.
     * @param offset number of rows to skip.
     * @return a list of matching {@link PlayerDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public java.util.List<PlayerDTO> getPlayersByNameLike(@org.jetbrains.annotations.NotNull String pattern, int limit, int offset) {
        final String sql = """
            Select uuid, name, first_seen, last_seen, playtime_seconds, can_be_ignored, nickname, last_seen_position_id
            FROM sb_players
            WHERE name LIKE ?
            LIMIT ? OFFSET ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pattern);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                final java.util.List<PlayerDTO> out = new java.util.ArrayList<>();
                while (rs.next()) out.add(mapPlayer(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search players by name LIKE " + pattern, e);
        }
    }

    /**
     * Retrieve players filtered by last_seen_position_id.
     * @param positionId the foreign key value to filter on.
     * @return a list of matching {@link PlayerDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public java.util.List<PlayerDTO> getPlayersByLastSeenPositionId(long positionId) {
        final String sql = """
            Select uuid, name, first_seen, last_seen, playtime_seconds, can_be_ignored, nickname, last_seen_position_id
            FROM sb_players
            WHERE last_seen_position_id = ?
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, positionId);
            try (ResultSet rs = stmt.executeQuery()) {
                final java.util.List<PlayerDTO> out = new java.util.ArrayList<>();
                while (rs.next()) out.add(mapPlayer(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get players by last_seen_position_id " + positionId, e);
        }
    }

    /**
     * Fast existence probe by UUID. Pure read.
     * @param uuid the player UUID.
     * @return true if a row exists, false otherwise.
     * @throws RuntimeException on SQL errors.
     */
    public boolean existsPlayer(@org.jetbrains.annotations.NotNull String uuid) {
        final String sql = "Select 1 FROM sb_players WHERE uuid = ? LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check existence for uuid " + uuid, e);
        }
    }

    /**
     * Total players count for pagination/metrics.
     * @return total number of rows in sb_players.
     * @throws RuntimeException on SQL errors.
     */
    public long countPlayers() {
        final String sql = "Select COUNT(*) FROM sb_players";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count players", e);
        }
    }

    public boolean hasAnyPlayers() {
        final String sql = "SELECT 1 FROM sb_players LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check if any players exist", e);
        }
    }

    // -- Update
    public Optional<PlayerDTO> updatePlayer(@NotNull String uuid, @NotNull PlayerDTO updatedPlayer) {
        final String sql = """
                UPDATE sb_players
                SET name = ?, first_seen = ?, last_seen = ?, playtime_seconds = ?, can_be_ignored = ?, nickname = ?, last_seen_position_id = ?
                WHERE uuid = ?
                RETURNING *
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, updatedPlayer.name());
            stmt.setString(2, updatedPlayer.firstSeen());
            stmt.setString(3, updatedPlayer.lastSeen());
            stmt.setLong(4, updatedPlayer.playtimeSeconds());
            stmt.setInt(5, updatedPlayer.canBeIgnored() ? 1 : 0);
            if (updatedPlayer.nickname() != null) {
                stmt.setString(6, updatedPlayer.nickname());
            } else {
                stmt.setNull(6, Types.VARCHAR);
            }
            if (updatedPlayer.lastSeenPositionId() != null) {
                stmt.setLong(7, updatedPlayer.lastSeenPositionId());
            } else {
                stmt.setNull(7, Types.BIGINT);
            }
            stmt.setString(8, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapPlayer(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update player with uuid " + uuid, e);
        }
    }

    /**
     * Increment playtime seconds atomically.
     * @param uuid the player UUID to update.
     * @param delta the increment (can be negative).
     * @return an {@link Optional} containing the refreshed {@link PlayerDTO} if updated, or empty if no row matched.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<PlayerDTO> incrementPlaytimeSeconds(@NotNull String uuid, long delta) {
        final String sql = "UPDATE sb_players SET playtime_seconds = playtime_seconds + ? WHERE uuid = ? RETURNING *";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, delta);
            stmt.setString(2, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapPlayer(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to increment playtime for uuid " + uuid, e);
        }
    }

    /**
     * Update last_seen to current time on the DB side.
     * Uses NOW() to avoid client clock skew.
     * @param uuid the player UUID to update.
     * @return an {@link Optional} containing the refreshed {@link PlayerDTO} if updated, or empty if no row matched.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<PlayerDTO> touchLastSeenNow(@NotNull String uuid) {
        final String sql = "UPDATE sb_players SET last_seen = NOW()::TEXT WHERE uuid = ? RETURNING *";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapPlayer(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to touch last_seen for uuid " + uuid, e);
        }
    }

    /**
     * Set or clear nickname (NULL to clear).
     * @param uuid the player UUID to update.
     * @param nickname the new nickname, or NULL to clear it.
     * @return an {@link Optional} containing the refreshed {@link PlayerDTO} if updated, or empty if no row matched.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<PlayerDTO> setNickname(@NotNull String uuid, String nickname) {
        final String sql = "UPDATE sb_players SET nickname = ? WHERE uuid = ? RETURNING *";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (nickname != null) stmt.setString(1, nickname); else stmt.setNull(1, Types.VARCHAR);
            stmt.setString(2, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapPlayer(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set nickname for uuid " + uuid, e);
        }
    }

    /**
     * Set or clear last_seen_position_id (NULL to clear).
     * @param uuid the player UUID to update.
     * @param positionId the position id, or NULL to clear it.
     * @return an {@link Optional} containing the refreshed {@link PlayerDTO} if updated, or empty if no row matched.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<PlayerDTO> setLastSeenPosition(@NotNull String uuid, Long positionId) {
        final String sql = "UPDATE sb_players SET last_seen_position_id = ? WHERE uuid = ? RETURNING *";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (positionId != null) stmt.setLong(1, positionId); else stmt.setNull(1, Types.BIGINT);
            stmt.setString(2, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapPlayer(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set last_seen_position_id for uuid " + uuid, e);
        }
    }
    // -- Delete

    /**
     * Delete a player by their UUID.
     * @param uuid the player UUID to delete.
     * @throws RuntimeException on SQL errors.
     */
    public void deletePlayerByUuid(@NotNull String uuid) {
        final Optional<PlayerDTO> playerToDelete = getPlayerByUuid(uuid);
        if (playerToDelete.isEmpty()) {
            return;
        }
        final String sql = """
                DELETE FROM sb_players
                WHERE uuid = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new RuntimeException("No lines deleted for uuid " + uuid);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete player with uuid " + uuid, e);
        }
    }

}
