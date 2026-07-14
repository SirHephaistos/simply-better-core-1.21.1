package as.sirhephaistos.simplybetter.core.db;


import as.sirhephaistos.simplybetter.library.AfkDTO;
import as.sirhephaistos.simplybetter.library.BackLocationDTO;
import as.sirhephaistos.simplybetter.library.PositionDTO;
import as.sirhephaistos.simplybetter.library.WarpDTO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <h1><img src="https://docs.godsmg.com/~gitbook/image?url=https%3A%2F%2F602320278-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_hKBWF%252Ficon%252FF3ga5TrIrIMXtWecHo3z%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252017_44_38.png%3Falt%3Dmedia%26token%3D8c3f45e4-ed6f-47ab-a4ab-474d24fa3bb3&width=32&dpr=1&quality=100&sign=2c456f01&sv=2"></img>
 * &nbsp;CRUD manager for {@link WarpDTO}
 * <img src="https://docs-sbs.godsmg.com/~gitbook/image?url=https%3A%2F%2F655127117-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_ofAiW%252Ficon%252F9SRBPTo3OKBsw5DvBwL3%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252000_07_28.png%3Falt%3Dmedia%26token%3D396dda36-5693-4638-b53e-59bf0770f309&width=32&dpr=1&quality=100&sign=55c114e6&sv=2"></img> </h1>
 * <h2>Create Methods</h2>
 * <ul>
 *     <li>{@link #createWarp(String, PositionDTO, String, String)}:</br>
 *         Creates a new warp with the given name, position, creator UUID and creation timestamp. Returns the created {@link WarpDTO}.</li>
 *     <li>{@link #createWarp(String, long, String, String)}:</br>
 *         Creates a new warp with the given name, position ID, creator UUID and creation timestamp. Returns the created {@link WarpDTO}.</li>
 *     <li>{@link #createWarp(String, PositionDTO)}:</br>
 *         Creates a new warp with the given name and position. Returns the created {@link WarpDTO}.</li>
 *     <li>{@link #createWarp(String, long)}:</br>
 *         Creates a new warp with the given name and position ID. Returns the created {@link WarpDTO}.</li>
 * </ul>
 * <h2>Read Methods</h2>
 * <ul>
 *     <li>{@link #getWarpById(long)}:</br>
 *         Retrieves a warp by its ID. Returns an {@link Optional} containing the {@link WarpDTO} if found, or empty if not found.</li>
 *     <li>{@link #getWarpByName(String)}:</br>
 *         Retrieves a warp by its name. Returns an {@link Optional} containing the {@link WarpDTO} if found, or empty if not found.</li>
 *     <li>{@link #getAllWarps()}:</br>
 *         Retrieves all warps. Returns a {@link List} of all {@link WarpDTO}.</li>
 *     <li>{@link #getAllWarpsPaged(int, int)}:</br>
 *         Retrieves all warps with pagination. Returns a {@link List} of {@link WarpDTO}.</li>
 *     <li>{@link #getWarpsMap()}:</br>
 *         Retrieves a map of warp names to their IDs. Returns a {@link Map} of warp names to their IDs.</li>
 *     <li>{@link #getWarpsMapPaged(int, int)}:</br>
 *         Retrieves a map of warp names to their IDs with pagination. Returns a {@link Map} of warp names to their IDs.</li>
 * </ul>
 * <h2>Update Methods</h2>
 * <ul>
 *     <li>{@link #updateWarpPosition(long, PositionDTO)}:</br>
 *         Updates the position of a warp. Returns an {@link Optional} containing the updated {@link WarpDTO} if found, or empty if not found.</li>
 *     <li>{@link #renameWarp(long, String)}:</br>
 *         Renames a warp. Returns an {@link Optional} containing the updated {@link WarpDTO} if found, or empty if not found.</li>
 * </ul>
 * <h2>Delete Methods</h2>
 * <ul>
 *     <li>{@link #deleteWarpById(long)}:</br>
 *          Deletes a warp by its ID.</li>
 *     <li>{@link #deleteWarpByName(String)}:</br>
 *          Deletes a warp by its name.</li>
 * </ul>
 *
 *<h3>General Information</h3>
 * @codeBaseStatus Complete
 * @testingStatus AwaitingJUnitTests
 * @author Sirhephaistos
 * @version 1.0
 */
@SuppressWarnings("ClassCanBeRecord")
public final class WarpsCrudManager {
    private final DatabaseManager db; // provides connections
    private static PositionsCrudManager positionsCrudManager;

    public WarpsCrudManager(DatabaseManager db, PositionsCrudManager positionsCrudManager) {
        this.db = db;
        WarpsCrudManager.positionsCrudManager = positionsCrudManager;
    }


    // -- Helpers

    /**
     * Privater helper to get a mounted {@link WarpDTO} from a {@link ResultSet}.
     * @param rs the {@link ResultSet}, positioned at the row to map.
     * @return the mapped {@link AfkDTO}.
     * @throws SQLException             on SQL errors coming from jdbc.
     * @throws IllegalArgumentException if rs is null.
     * @throws IllegalStateException    if any non-nullable column is null.
     */
    /**
     * Column list for SELECT queries that JOIN sb_positions.
     */
    private static final String WARP_JOIN_COLUMNS =
            "w.id, w.name, w.created_at, w.created_by_uuid, w.position_id, " +
            "p.dimension_id, p.x, p.y, p.z, p.orientation_yrotation, p.orientation_xrotation";

    private static WarpDTO mapWarp(ResultSet rs) throws SQLException {
        if (rs == null) throw new IllegalArgumentException("rs is null");

        final long id = rs.getLong("id");
        if (rs.wasNull()) throw new IllegalStateException("id is null");
        @NotNull final String name = rs.getString("name");
        if (name == null) throw new IllegalStateException("name is null");
        final long positionId = rs.getLong("position_id");
        if (rs.wasNull()) throw new IllegalStateException("position_id is null");
        @Nullable final String createdBy = rs.getString("created_by_uuid");
        @NotNull final String createdAt = rs.getString("created_at");
        if (createdAt == null) throw new IllegalStateException("created_at is null");
        @NotNull final PositionDTO position = new PositionDTO(
                positionId,
                rs.getString("dimension_id"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("orientation_yrotation"),
                rs.getFloat("orientation_xrotation")
        );
        return new WarpDTO(id, name, position, createdBy, createdAt);
    }

    /**
     * Reformats a string to be used as a warp name.
     * Removes all non-alphanumeric characters and converts to lowercase.
     * @param s the string to reformat.
     * @return the reformatted string.
     * @throws IllegalArgumentException if s is null.
     */
    private static String strReformat(String s) {
        if (s == null) {
            throw new IllegalArgumentException("String is null");
        }
        return s.replaceAll("[^a-zA-Z0-9_]", "").replace(" ", "_").toLowerCase();
    }


    // -- Create

    /**
     * Create a new warp from a {@code name} and a {@link PositionDTO}.
     * @param name string name of the warp (will be reformatted by {@link #strReformat(String)}).
     * @param positionDTO a {@link PositionDTO} representing the position of the warp.
     * @param createdBy {@code playerUUID} who created the warp, or {@code null} if created by the system.
     * @param createdAt timestamp string of when the warp was created.
     * @return the created {@link WarpDTO}.
     * @throws IllegalArgumentException if {@code name} or {@code positionDTO} is null.
     * @throws IllegalStateException if the position could not be created.
     * @throws RuntimeException on SQL errors.
     */
    public WarpDTO createWarp(@NotNull String name, @NotNull PositionDTO positionDTO, @Nullable String createdBy, @Nullable String createdAt) {
        name = strReformat(name);
        if (positionDTO.id() == null) {
            positionDTO = positionsCrudManager.createPosition(positionDTO);
            if (positionDTO.id() == null) {
                throw new IllegalStateException("Failed to create position for warp");
            }
        }
        String sql = "INSERT INTO sb_warps (name, created_at, created_by_uuid, position_id) VALUES (?, ?, ?, ?) " +
                "RETURNING id, name, created_at, created_by_uuid, position_id";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            if (createdAt != null) {
                ps.setString(2, createdAt);
            } else {
                ps.setString(2, Instant.now().toString());
            }
            if (createdBy != null) {
                ps.setString(3, createdBy);
            } else {
                ps.setNull(3, java.sql.Types.VARCHAR);
            }
            ps.setLong(4, positionDTO.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("INSERT did not return a row");
                }
                final long id = rs.getLong("id");
                final String returnedName = rs.getString("name");
                final String returnedCreatedAt = rs.getString("created_at");
                final String returnedCreatedBy = rs.getString("created_by_uuid");
                return new WarpDTO(id, returnedName, positionDTO, returnedCreatedBy, returnedCreatedAt);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create warp", e);
        }
    }

    /**
     * Create a new warp from a {@code name} and a {@link PositionDTO}.
     * @param name string name of the warp (will be reformatted by {@link #strReformat(String)}).
     * @param idPosition the id of the position where the warp will be created.
     * @param createdBy {@code playerUUID} who created the warp, or {@code null} if created by the system.
     * @param createdAt timestamp string of when the warp was created.
     * @return the created {@link WarpDTO}.
     * @throws IllegalArgumentException if {@code name} or {@code positionDTO} is null.
     * @throws IllegalStateException if the position could not be created.
     * @throws RuntimeException on SQL errors.
     */
    public WarpDTO createWarp(@NotNull String name,long idPosition, @Nullable String createdBy, @NotNull String createdAt){
        var positionDTO = positionsCrudManager.getPositionById(idPosition).orElseThrow(()->new IllegalArgumentException("Position with id "+idPosition+" does not exist"));
        return createWarp(name,positionDTO,createdBy,createdAt);
    }

    /**
     * Create a new warp from a {@code name} and a {@link PositionDTO}.
     * @param name string name of the warp (will be reformatted by {@link #strReformat(String)}).
     * @param positionDTO a {@link PositionDTO} representing the position of the warp.
     * @return the created {@link WarpDTO}.
     * @throws IllegalArgumentException if {@code name} or {@code positionDTO} is null.
     * @throws IllegalStateException if the position could not be created.
     * @throws RuntimeException on SQL errors.
     */
    public WarpDTO createWarp(@NotNull String name, @NotNull PositionDTO positionDTO){
        return createWarp(name,positionDTO,null,null);
    }

    /**
     * Create a new warp from a {@code name} and a {@link PositionDTO}.
     * @param name string name of the warp (will be reformatted by {@link #strReformat(String)}).
     * @param idPosition the id of the position where the warp will be created.
     * @return the created {@link WarpDTO}.
     * @throws IllegalArgumentException if {@code name} or {@code positionDTO} is null.
     * @throws IllegalStateException if the position could not be created.
     * @throws RuntimeException on SQL errors.
     */
    public WarpDTO createWarp(@NotNull String name,long idPosition){
        var positionDTO = positionsCrudManager.getPositionById(idPosition).orElseThrow(()->new IllegalArgumentException("Position with id "+idPosition+" does not exist"));
        return createWarp(name,positionDTO,null,null);
    }

    // -- Read

    /**
     * Get a warp by its id.
     * @param id the id of the warp.
     * @return an {@link Optional} containing the {@link WarpDTO} if found, or empty if not found.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<WarpDTO> getWarpById(long id) {
        final String sql = "SELECT " + WARP_JOIN_COLUMNS + " FROM sb_warps w JOIN sb_positions p ON w.position_id = p.id WHERE w.id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapWarp(rs));
                } else {
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get warp by id=" + id, e);
        }
    }

    /**
     * Get a warp by its name.
     * @param name the name of the warp.
     * @return an {@link Optional} containing the {@link WarpDTO} if found, or empty if not found.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<WarpDTO> getWarpByName(@NotNull String name) {
        name = strReformat(name);
        final String sql = "SELECT " + WARP_JOIN_COLUMNS + " FROM sb_warps w JOIN sb_positions p ON w.position_id = p.id WHERE w.name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapWarp(rs));
                } else {
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get warp by name=" + name, e);
        }
    }

    /**
     * Get all warps.
     * @return a {@link List} of all {@link WarpDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public List<WarpDTO> getAllWarps() {
        final String sql = "SELECT " + WARP_JOIN_COLUMNS + " FROM sb_warps w JOIN sb_positions p ON w.position_id = p.id";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<WarpDTO> warps = new java.util.ArrayList<>();
            while (rs.next()) {
                warps.add(mapWarp(rs));
            }
            return warps;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get all warps", e);
        }
    }

    /**
     * Get all warps with pagination.
     * @param limit  the maximum number of warps to return.
     * @param offset the offset from which to start returning warps.
     * @return a {@link List} of {@link WarpDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public List<WarpDTO> getAllWarpsPaged(int limit, int offset) {
        final String sql = "SELECT " + WARP_JOIN_COLUMNS + " FROM sb_warps w JOIN sb_positions p ON w.position_id = p.id LIMIT ? OFFSET ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                List<WarpDTO> warps = new java.util.ArrayList<>();
                while (rs.next()) {
                    warps.add(mapWarp(rs));
                }
                return warps;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get all warps paged", e);
        }
    }

    /**
     * Get a map of warp names to their ids.
     * @return a {@link Map} of warp names to their ids.
     * @throws RuntimeException on SQL errors.
     */
    public Map<String,@NotNull Long> getWarpsMap(){
        // do a custom query to get name and id only
        final String sql = "SELECT id, name FROM sb_warps";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return getStringLongMap(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get warps map", e);
        }
    }

    /**
     * Get a map of warp names to their ids with pagination.
     * @param limit  the maximum number of warps to return.
     * @param offset the offset from which to start returning warps.
     * @return a {@link Map} of warp names to their ids.
     * @throws RuntimeException on SQL errors.
     */
    public Map<String,@NotNull Long> getWarpsMapPaged(int limit, int offset){
        // do a custom query to get name and id only
        final String sql = "SELECT id, name FROM sb_warps LIMIT ? OFFSET ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                return getStringLongMap(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get warps map paged", e);
        }
    }

    /**
     * Private helper to get a map of warp names to their ids from a {@link ResultSet}.
     * @param rs the {@link ResultSet} to read from.
     * @return a {@link Map} of warp names to their ids.
     * @throws SQLException on SQL errors.
     */
    private Map<String, @NotNull Long> getStringLongMap(ResultSet rs) throws SQLException {
        Map<String, Long> warpsMap = new java.util.HashMap<>();
        while (rs.next()) {
            final long id = rs.getLong("id");
            if (rs.wasNull()) throw new IllegalStateException("id is null");
            @NotNull final String name = rs.getString("name");
            if (name == null) throw new IllegalStateException("name is null");
            warpsMap.put(name, id);
        }
        return warpsMap;
    }
    // -- Update

    /**
     * Update the position of a warp.
     * @param warpId      the id of the warp to update.
     * @param newPosition the new {@link PositionDTO} for the warp.
     * @return an {@link Optional} containing the updated {@link WarpDTO} if found, or empty if not found.
     * @throws IllegalArgumentException if {@code newPosition} is null.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<WarpDTO> updateWarpPosition(long warpId, @NotNull PositionDTO newPosition) {
        PositionDTO positionToUpdate;
        if (newPosition.id() == null) {
            positionToUpdate = positionsCrudManager.createPosition(newPosition);
        } else {
            positionToUpdate = newPosition;
        }
        final String sql = "UPDATE sb_warps SET position_id = ? WHERE id = ? RETURNING id";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, positionToUpdate.id());
            ps.setLong(2, warpId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update warp position for warp id=" + warpId, e);
        }
        return getWarpById(warpId);
    }

    /**
     * Rename a warp.
     * @param warpId  the id of the warp to rename.
     * @param newName the new name for the warp.
     * @return an {@link Optional} containing the updated {@link WarpDTO} if found, or empty if not found.
     * @throws IllegalArgumentException if {@code newName} is null.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<WarpDTO> renameWarp(long warpId, @NotNull String newName) {
        newName = strReformat(newName);
        final String sql = "UPDATE sb_warps SET name = ? WHERE id = ? RETURNING id";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setLong(2, warpId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Warp with id " + warpId + " does not exist");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to rename warp id=" + warpId, e);
        }
        return getWarpById(warpId);
    }

    // -- Delete

    /**
     * Delete a warp by its id.
     * @param id the id of the warp to delete.
     * @throws IllegalArgumentException if the warp does not exist.
     * @throws RuntimeException on SQL errors.
     */
    public void deleteWarpById(long id) {
        if (getWarpById(id).isEmpty()) {
            throw new IllegalArgumentException("Warp with id " + id + " does not exist");
        }
        final String sql = "DELETE FROM sb_warps WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            final int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new IllegalStateException("No warp deleted with id " + id);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete warp by id=" + id, e);
        }
    }

    /**
     * Delete a warp by its name.
     * @param name the name of the warp to delete.
     * @throws IllegalArgumentException if the warp does not exist.
     * @throws RuntimeException on SQL errors.
     */
    public void deleteWarpByName(@NotNull String name) {
        name = strReformat(name);
        final Optional<WarpDTO> warpOpt = getWarpByName(name);
        if (warpOpt.isEmpty()) {
            throw new IllegalArgumentException("Warp with name " + name + " does not exist");
        }
        final String sql = "DELETE FROM sb_warps WHERE name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            final int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new IllegalStateException("No warp deleted with name " + name);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete warp by name=" + name, e);
        }
    }
}
