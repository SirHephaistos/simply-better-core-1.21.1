package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.AfkDTO;
import as.sirhephaistos.simplybetter.library.BackLocationDTO;
import as.sirhephaistos.simplybetter.library.PositionDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <h1><img src="https://docs.godsmg.com/~gitbook/image?url=https%3A%2F%2F602320278-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_hKBWF%252Ficon%252FF3ga5TrIrIMXtWecHo3z%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252017_44_38.png%3Falt%3Dmedia%26token%3D8c3f45e4-ed6f-47ab-a4ab-474d24fa3bb3&width=32&dpr=1&quality=100&sign=2c456f01&sv=2"></img>
 * &nbsp;CRUD manager for {@link BackLocationDTO}
 * <img src="https://docs-sbs.godsmg.com/~gitbook/image?url=https%3A%2F%2F655127117-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_ofAiW%252Ficon%252F9SRBPTo3OKBsw5DvBwL3%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252000_07_28.png%3Falt%3Dmedia%26token%3D396dda36-5693-4638-b53e-59bf0770f309&width=32&dpr=1&quality=100&sign=55c114e6&sv=2"></img> </h1>
 * <h2>Create Methods</h2>
 * <ul>
 *     <li>{@link #method}:</br>
 *         Description. And returns {}. </li>
 * </ul>
 * <h2>Read Methods</h2>
 * <ul>
 *     <li>{@link #method}:</br>
 *         Description. And returns {}. </li>
 * </ul>
 * <h2>Update Methods</h2>
 * <ul>
 *     <li>{@link #method}:</br>
 *         Description. And returns {}. </li>
 * </ul>
 * <h2>Delete Methods</h2>
 * <ul>
 *     <li>{@link #method}:</br>
 *         Description</li>
 * </ul>
 *
 *<h3>General Information</h3>
 * @codeBaseStatus Complete
 * @testingStatus AwaitingJUnitTests
 * @author Sirhephaistos
 * @version 1.0
 */
public final class BackLocationsCrudManager {
    private final DatabaseManager db;

    public BackLocationsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    /**
     * Privater helper to get a mounted {@link BackLocationDTO} from a {@link ResultSet}.
     * @param rs the {@link ResultSet}, positioned at the row to map.
     * @return the mapped {@link AfkDTO}.
     * @throws SQLException on SQL errors coming from jdbc.
     * @throws IllegalArgumentException if rs is null.
     * @throws IllegalStateException if any non-nullable column is null.
     */
    private static BackLocationDTO map(@NotNull ResultSet rs) throws SQLException {
        if (rs == null) throw new IllegalArgumentException("ResultSet must not be null");
        if (rs.getString("player_uuid") == null)
            throw new IllegalStateException("player_uuid column is null");
        if (rs.getString("updated_at") == null)
            throw new IllegalStateException("updated_at column is null");
        final String playerUuid = rs.getString("player_uuid");
        final String updatedAt = rs.getString("updated_at");
        final long prev = rs.getLong("previous_position_id");
        final long curr = rs.getLong("current_position_id");
        return new BackLocationDTO(playerUuid, updatedAt, prev, curr);
    }

    // -- Create

    /**
     * Creates a new back location entry for the given {@code playerUuid}.
     * @param playerUuid {@code playerUuid}.
     * @param updatedAt timestamp string to persist.
     * @param previousPosition Previous {@link PositionDTO}.
     * @param currentPosition Current {@link PositionDTO}.
     * @return The created {@link BackLocationDTO}.
     * @throws IllegalArgumentException if a back location for the {@code playerUuid}. already exists.
     * @throws RuntimeException on SQL errors or if no rows were affected.
     */
    public BackLocationDTO createBackLocation(@NotNull String playerUuid,
                                              @NotNull String updatedAt,
                                              PositionDTO previousPosition,
                                              PositionDTO currentPosition) {
        if (getBackLocationByPlayerUuid(playerUuid).isEmpty()) {
            throw new IllegalArgumentException("Back location for playerUuid=" + playerUuid + " already exists");
        }
        PositionsCrudManager positionsCrudManager = new PositionsCrudManager(db);
        if (previousPosition.id() == null) {
            previousPosition = positionsCrudManager.createPosition(
                    previousPosition.dimensionId(),
                    previousPosition.x(),
                    previousPosition.y(),
                    previousPosition.z(),
                    previousPosition.yaw(),
                    previousPosition.pitch()
            );
        }
        if (currentPosition.id() == null) {
            currentPosition = positionsCrudManager.createPosition(
                    currentPosition.dimensionId(),
                    currentPosition.x(),
                    currentPosition.y(),
                    currentPosition.z(),
                    currentPosition.yaw(),
                    currentPosition.pitch()
            );
        }
        if (previousPosition.id() == null || currentPosition.id() == null) {
            throw new IllegalStateException("Position IDs must not be null after creation");
        }
        String id;
        final String sql = """
                INSERT INTO sb_back_locations (player_uuid, updated_at, previous_position_id, current_position_id)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setString(2, updatedAt);
            ps.setLong(3, previousPosition.id());
            ps.setLong(4, currentPosition.id());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new RuntimeException("No generated keys");
                }
                id = keys.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creating back location for playerUuid=" + playerUuid, e);
        }
        return getBackLocationByPlayerUuid(id).orElseThrow(() ->
                new RuntimeException("Post-fetch missing for playerUuid=" + playerUuid));
    }

    // -- Read

    /**
     * Fetches a back location by {@code playerUuid}.
     * @param playerUuid {@code playerUuid}.
     * @return Optional containing the {@link BackLocationDTO} or empty if none found.
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
            throw new RuntimeException("Error fetching back location for playerUuid=" + playerUuid, e);
        }
    }

    /**
     * Lists all back locations.
     * @return List of all {@link BackLocationDTO}, ordered by updated_at descending.
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
            throw new RuntimeException("Error listing all back locations", e);
        }
    }

    /**
     * Lists back locations in a paged manner.
     * @param limit Maximum number of entries to return.
     * @param offset Number of entries to skip.
     * @return List of {@link BackLocationDTO}, ordered by updated_at descending.
     * @throws RuntimeException on SQL errors.
     */
    public List<BackLocationDTO> getAllBackLocationsPaged(int limit, int offset){
        final String sql = """
                SELECT player_uuid, updated_at, previous_position_id, current_position_id
                FROM sb_back_locations
                ORDER BY updated_at DESC
                LIMIT ? OFFSET ?
                """;
        final List<BackLocationDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing paged back locations", e);
        }
    }

    // -- Update

    /**
     * Updates an existing back location for the given {@code playerUuid}.
     * @param playerUuid {@code playerUuid}.
     * @param previousPosition Previous {@link PositionDTO}.
     * @param newPosition New {@link PositionDTO}.
     * @param updatedAt timestamp string to persist.
     * @return The updated {@link BackLocationDTO}.
     * @throws IllegalArgumentException if a back location for the {@code playerUuid}. does not exist.
     * @throws RuntimeException on SQL errors or if no rows were affected.
     */
    public BackLocationDTO updateBackLocation(@NotNull String playerUuid,PositionDTO previousPosition, PositionDTO newPosition, @NotNull String updatedAt) {
        if (!getBackLocationByPlayerUuid(playerUuid).isPresent()) {
            throw new IllegalArgumentException("Back location for playerUuid=" + playerUuid + " does not exist");
        }
        deleteBackLocationByPlayerUuid(playerUuid);
        return createBackLocation(playerUuid, updatedAt, previousPosition, newPosition);
    }

    // ------------------------- helpers -------------------------

    /**
     * Deletes a back location by {@code playerUuid}.
     * @param playerUuid {@code playerUuid}.
     * @throws IllegalArgumentException if a back location for the {@code playerUuid}. does not exist.
     * @throws RuntimeException on SQL errors or if no rows were affected.
     */
    public void deleteBackLocationByPlayerUuid(@NotNull String playerUuid) {
        if (getBackLocationByPlayerUuid(playerUuid).isEmpty()) {
            throw new IllegalArgumentException("Back location for playerUuid=" + playerUuid + " does not exist");
        }
        final String sql = "DELETE FROM sb_back_locations WHERE player_uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            final int affected = ps.executeUpdate();
            if (affected == 0) throw new RuntimeException("No back location deleted for playerUuid=" + playerUuid);
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting back location for playerUuid=" + playerUuid, e);
        }
    }
}
