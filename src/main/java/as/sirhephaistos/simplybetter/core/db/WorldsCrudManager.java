package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.WorldDTO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <h1><img src="https://docs.godsmg.com/~gitbook/image?url=https%3A%2F%2F602320278-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_hKBWF%252Ficon%252FF3ga5TrIrIMXtWecHo3z%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252017_44_38.png%3Falt%3Dmedia%26token%3D8c3f45e4-ed6f-47ab-a4ab-474d24fa3bb3&width=32&dpr=1&quality=100&sign=2c456f01&sv=2"></img>
 * &nbsp;CRUD manager for {@link WorldDTO}
 * <img src="https://docs-sbs.godsmg.com/~gitbook/image?url=https%3A%2F%2F655127117-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_ofAiW%252Ficon%252F9SRBPTo3OKBsw5DvBwL3%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252000_07_28.png%3Falt%3Dmedia%26token%3D396dda36-5693-4638-b53e-59bf0770f309&width=32&dpr=1&quality=100&sign=55c114e6&sv=2"></img> </h1>
 * <h2>Create Methods</h2>
 * <ul>
 *     <li>{@link #createWorld}:</br>
 *         Creates a new world entry for the given dimension ID and optional center position ID. Returns the created {@link WorldDTO}.</li>
 * </ul>
 * <h2>Read Methods</h2>
 * <ul>
 *     <li>{@link #getWorldById}:</br>
 *         Retrieves a world by its unique ID. Returns an {@link Optional} containing the {@link WorldDTO} if found, or empty if not found.</li>
 *     <li>{@link #getWorldByDimensionId}:</br>
 *         Retrieves a world by its unique dimension ID. Returns an {@link Optional} containing the {@link WorldDTO} if found, or empty if not found.</li>
 *     <li>{@link #getAllWorlds}:</br>
 *         Retrieves all worlds in the database. Returns a {@link List} of {@link WorldDTO}.</li>
 *     <li>{@link #getAllWorldsPaged}:</br>
 *         Retrieves a paginated list of worlds. Accepts limit and offset parameters. Returns a {@link List} of {@link WorldDTO}.</li>
 * </ul>
 * <h2>Update Methods</h2>
 * <ul>
 *     <li>{@link #renameWorldById}:</br>
 *         Renames a world by its unique ID. Accepts the new dimension ID. Returns an {@link Optional} containing the updated {@link WorldDTO} if found, or empty if not found.</li>
 *     <li>{@link #renameWorldByDimensionId}:</br>
 *         Renames a world by its current dimension ID. Accepts the new dimension ID. Returns an {@link Optional} containing the updated {@link WorldDTO} if found, or empty if not found.</li>
 *     <li>{@link #updateCenterPositionById}:</br>
 *         Updates the center position ID of a world by its unique ID. Accepts the new center position ID (nullable). Returns an {@link Optional} containing the updated {@link WorldDTO} if found, or empty if not found.</li>
 *     <li>{@link #updateCenterPositionByDimensionId}:</br>
 *         Updates the center position ID of a world by its dimension ID. Accepts the new center position ID (nullable). Returns an {@link Optional} containing the updated {@link WorldDTO} if found, or empty if not found.</li>
 * </ul>
 * <h2>Delete Methods</h2>
 * <ul>
 *     <li>{@link #deleteWorldById}:</br>
 *         Deletes a world by its unique ID. Returns an {@link Optional} containing the deleted {@link WorldDTO} if found, or empty if not found.</li>
 *     <li>{@link #deleteWorldByDimensionId}:</br>
 *         Deletes a world by its dimension ID. Returns an {@link Optional} containing the deleted {@link WorldDTO} if found, or empty if not found.</li>
 * </ul>
 *
 *<h3>General Information</h3>
 * @codeBaseStatus Complete
 * @testingStatus AwaitingJUnitTests
 * @author Sirhephaistos
 * @version 1.0
 */
@SuppressWarnings("ClassCanBeRecord")
public final class WorldsCrudManager {
    private final DatabaseManager db;

    public WorldsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    /**
     * Private helper to get as mounted {@link WorldDTO} from a {@link ResultSet}
     * @param rs the {@link ResultSet}, positioned at the desired row to map.
     * @return the mapped {@link WorldDTO}
     * @throws SQLException on SQL errors coming from jdbc.
     * @throws IllegalArgumentException if rs is null.
     * @throws IllegalStateException if any non-nullable column is null.
     */
    private static WorldDTO mapWorld(ResultSet rs) throws SQLException {
        if (rs == null) throw new IllegalArgumentException("ResultSet cannot be null");
        if (rs.getLong("id") == 0) throw new IllegalStateException("ID cannot be null");
        if (rs.getString("dimension_id") == null)
            throw new IllegalStateException("Dimension ID cannot be null");
        final long id = rs.getLong("id");
        final String dimensionId = rs.getString("dimension_id");
        final Long centerPositionId = (rs.getLong("center_position_id") == 0) ? null : rs.getLong("center_position_id");
        return new WorldDTO(id, dimensionId, centerPositionId);
    }
    // -- Create

    /**
     * Creates a new world entry for the given dimension ID and optional center position ID.
     * @param dimensionId the unique dimension ID for the new world. Cannot be null.
     * @param centerPositionId the optional center position ID for the new world. Can be null.
     * @return the created {@link WorldDTO}.
     * @throws IllegalArgumentException if a world with the given dimension ID already exists.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public WorldDTO createWorld(@NotNull String dimensionId,@Nullable Long centerPositionId) {
        if (getWorldByDimensionId(dimensionId).isPresent()) {
            throw new IllegalArgumentException("World with dimension_id=" + dimensionId + " already exists");
        }
        final String sql = """
                INSERT INTO sb_worlds (dimension_id, center_position_id)
                VALUES (?, ?)
                """;
        long id;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, dimensionId);
            if (centerPositionId == null) {
                ps.setNull(2, Types.BIGINT);
            } else {
                ps.setLong(2, centerPositionId);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new RuntimeException("No generated key");
                }
                id = keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting world for dimension=" + dimensionId, e);
        }
        return getWorldById(id).orElseThrow(() -> new RuntimeException("Error fetching newly created world with id=" + id));
    }

    // -- Read

    /**
     * Retrieves a world by its unique ID.
     * @param id the unique ID of the world.
     * @return an {@link Optional} containing the {@link WorldDTO} if found, or empty if not found.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public Optional<WorldDTO> getWorldById(long id) {
        final String sql = """
                SELECT id, dimension_id, center_position_id
                FROM sb_worlds
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapWorld(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching world by id=" + id, e);
        }
    }

    /**
     * Retrieves a world by its unique dimension ID.
     * @param dimensionId the unique dimension ID of the world.
     * @return an {@link Optional} containing the {@link WorldDTO} if found, or empty if not found.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public Optional<WorldDTO> getWorldByDimensionId(@NotNull String dimensionId) {
        final String sql = """
                SELECT id, dimension_id, center_position_id
                FROM sb_worlds
                WHERE dimension_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dimensionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapWorld(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching world by unique dimension_id=" + dimensionId, e);
        }
    }

    /**
     * Retrieves all worlds in the database.
     * @return a {@link List} of {@link WorldDTO}.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public List<WorldDTO> getAllWorlds() {
        final String sql = """
                SELECT id, dimension_id, center_position_id
                FROM sb_worlds
                ORDER BY id ASC
                """;
        final List<WorldDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(mapWorld(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing worlds", e);
        }
    }

    /**
     * Retrieves a paginated list of worlds.
     * @param limit the maximum number of worlds to retrieve.
     * @param offset the number of worlds to skip before starting to collect the result set.
     * @return a {@link List} of {@link WorldDTO}.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public List<WorldDTO> getAllWorldsPaged(int limit, int offset) {
        final String sql = """
                SELECT id, dimension_id, center_position_id
                FROM sb_worlds
                ORDER BY id ASC
                LIMIT ? OFFSET ?
                """;
        final List<WorldDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapWorld(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing paged worlds", e);
        }
    }

    // -- Update

    /**
     * Renames a world by its unique ID.
     * @param id the unique ID of the world to rename.
     * @param newDimensionId the new dimension ID for the world.
     * @return an {@link Optional} containing the updated {@link WorldDTO} if found, or empty if not found.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public Optional<WorldDTO> renameWorldById(long id, @NotNull String newDimensionId) {
        final Optional<WorldDTO> before = getWorldById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_worlds
                SET dimension_id = ?
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newDimensionId);
            ps.setLong(2, id);
            final int affected = ps.executeUpdate();
            if (affected == 0) throw new RuntimeException("No world updated for id=" + id);
            return getWorldById(id);
        } catch (SQLException e) {
            throw new RuntimeException("Error renaming world id=" + id, e);
        }
    }

    /**
     * Renames a world by its current dimension ID.
     * @param currentDimensionId the current dimension ID of the world to rename.
     * @param newDimensionId the new dimension ID for the world.
     * @return an {@link Optional} containing the updated {@link WorldDTO} if found, or empty if not found.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public Optional<WorldDTO> renameWorldByDimensionId(@NotNull String currentDimensionId, @NotNull String newDimensionId) {
        final Optional<WorldDTO> before = getWorldByDimensionId(currentDimensionId);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_worlds
                SET dimension_id = ?
                WHERE dimension_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newDimensionId);
            ps.setString(2, currentDimensionId);
            final int affected = ps.executeUpdate();
            if (affected == 0) throw new RuntimeException("No world updated for dimension_id=" + currentDimensionId);
            return getWorldByDimensionId(newDimensionId);
        } catch (SQLException e) {
            throw new RuntimeException("Error renaming world dimension_id=" + currentDimensionId, e);
        }
    }

    /**
     * Updates the center position ID of a world by its unique ID.
     * @param id the unique ID of the world to update.
     * @param newCenterPositionId the new center position ID for the world. Can be null.
     * @return an {@link Optional} containing the updated {@link WorldDTO} if found, or empty if not found.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public Optional<WorldDTO> updateCenterPositionById(long id, @Nullable Long newCenterPositionId) {
        final Optional<WorldDTO> before = getWorldById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_worlds
                SET center_position_id = ?
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (newCenterPositionId == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, newCenterPositionId);
            }
            ps.setLong(2, id);
            final int affected = ps.executeUpdate();
            if (affected == 0) throw new RuntimeException("No world updated for id=" + id);
            return getWorldById(id);
        } catch (SQLException e) {
            throw new RuntimeException("Error updating center_position_id for world id=" + id, e);
        }
    }

    /**
     * Updates the center position ID of a world by its dimension ID.
     * @param dimensionId the unique dimension ID of the world to update.
     * @param newCenterPositionId the new center position ID for the world. Can be null.
     * @return an {@link Optional} containing the updated {@link WorldDTO} if found, or empty if not found.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public Optional<WorldDTO> updateCenterPositionByDimensionId(@NotNull String dimensionId, @Nullable Long newCenterPositionId) {
        final Optional<WorldDTO> before = getWorldByDimensionId(dimensionId);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_worlds
                SET center_position_id = ?
                WHERE dimension_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (newCenterPositionId == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, newCenterPositionId);
            }
            ps.setString(2, dimensionId);
            final int affected = ps.executeUpdate();
            if (affected == 0) throw new RuntimeException("No world updated for dimension_id=" + dimensionId);
            return getWorldByDimensionId(dimensionId);
        } catch (SQLException e) {
            throw new RuntimeException("Error updating center_position_id for world dimension_id=" + dimensionId, e);
        }
    }

    // -- Delete

    /**
     * Deletes a world by its unique ID.
     * @param id the unique ID of the world to delete.
     * @return an {@link Optional} containing the deleted {@link WorldDTO} if found, or empty if not found.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public Optional<WorldDTO> deleteWorldById(long id) {
        final Optional<WorldDTO> before = getWorldById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = "DELETE FROM sb_worlds WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            final int affected = ps.executeUpdate();
            if (affected == 0) throw new RuntimeException("No world deleted for id=" + id);
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting world id=" + id, e);
        }
    }

    /**
     * Deletes a world by its dimension ID.
     * @param dimensionId the unique dimension ID of the world to delete.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public void deleteWorldByDimensionId(@NotNull String dimensionId) {
        if (getWorldByDimensionId(dimensionId).isPresent()){
            throw new IllegalArgumentException("World with dimension_id=" + dimensionId + " does not exist");
        }
        final String sql = "DELETE FROM sb_worlds WHERE dimension_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dimensionId);
            ps.executeUpdate();
            if (ps.getUpdateCount() == 0) throw new RuntimeException("No world deleted for dimension_id=" + dimensionId);
            if (getWorldByDimensionId(dimensionId).isPresent()) {
                throw new RuntimeException("World with dimension_id=" + dimensionId + " still exists after deletion");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting world dimension_id=" + dimensionId, e);
        }
    }
}
