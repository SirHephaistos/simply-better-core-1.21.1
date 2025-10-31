package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.PositionDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
/**
 * <h1><img src="https://docs.godsmg.com/~gitbook/image?url=https%3A%2F%2F602320278-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_hKBWF%252Ficon%252FF3ga5TrIrIMXtWecHo3z%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252017_44_38.png%3Falt%3Dmedia%26token%3D8c3f45e4-ed6f-47ab-a4ab-474d24fa3bb3&width=32&dpr=1&quality=100&sign=2c456f01&sv=2"></img>
 * &nbsp;CRUD manager for {@link PositionDTO}
 * <img src="https://docs-sbs.godsmg.com/~gitbook/image?url=https%3A%2F%2F655127117-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_ofAiW%252Ficon%252F9SRBPTo3OKBsw5DvBwL3%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252000_07_28.png%3Falt%3Dmedia%26token%3D396dda36-5693-4638-b53e-59bf0770f309&width=32&dpr=1&quality=100&sign=55c114e6&sv=2"></img> </h1>
 * <h2>Create Methods</h2>
 * <ul>
 *     <li>{@link #createPosition(String, double, double, double, float, float)}:</br>
 *     Creates a new position in the database and returns its generated ID. </li>
 * </ul>
 * <h2>Read Methods</h2>
 * <ul>
 *     <li>{@link #getPositionById(long)}:</br>
 *         Fetches a position by its ID. Returns an {@link Optional} containing the {@link PositionDTO} if found, or empty if not found. </li>
 *     <li>{@link #getAllPositions()}:</br>
 *         Retrieves all positions from the database. Returns a list of {@link PositionDTO} objects. </li>
 * </ul>
 * <h2>Update Methods</h2>
 * <ul>
 *     <li>{@link #updatePosition(long, PositionDTO)}:</br> #NOT_RECOMMENDED WHY UPDATE A POSITION? USE DELETE + CREATE INSTEAD.
 *         Updates an existing position identified by its ID with new data from a {@link PositionDTO} object.
 *         Returns an {@link Optional} containing the updated {@link PositionDTO} if successful, or empty if the position was not found. </li>
 * </ul>
 * <h2>Delete Methods</h2>
 * <ul>
 *     <li>{@link #deletePositionById(long)}:</br>
 *         Deletes a position by its ID. Returns an {@link Optional} containing the deleted {@link PositionDTO} if found and deleted, or empty if not found. </li>
 *     <li>{@link #deleteUnusedPositions()}:</br>
 *         Deletes all positions that are not referenced by any other records in the database.
 *         Returns the number of deleted positions. </li>
 * </ul>
 *
 *<h3>General Information</h3>
 * @codeBaseStatus Complete
 * @testingStatus AwaitingJUnitTests
 * @author Sirhephaistos
 * @version 1.0
 */
@SuppressWarnings("ClassCanBeRecord")
public final class PositionsCrudManager {
    private final DatabaseManager db;

    public PositionsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    /**
     * Private helper to get as mounted {@link PositionDTO} from a {@link ResultSet}
     * @param rs the {@link ResultSet}, positioned at the desired row to map.
     * @return the mapped {{@link PositionDTO}
     * @throws SQLException on SQL errors coming from jdbc.
     * @throws IllegalArgumentException if rs is null.
     * @throws IllegalStateException if any non-nullable column is null.
     */
    private static PositionDTO mapPosition(ResultSet rs) throws SQLException {
        if (rs == null) throw new IllegalArgumentException("ResultSet cannot be null");
        if (rs.getString("dimension_id") == null)
            throw new IllegalStateException("dimension_id cannot be null in sb_positions");
        final Long id = rs.getLong("id");
        final String dimensionId = rs.getString("dimension_id");
        final double x = rs.getDouble("x");
        final double y = rs.getDouble("y");
        final double z = rs.getDouble("z");
        final float yaw = rs.getFloat("orientation_yaw");
        final float pitch = rs.getFloat("orientation_pitch");
        return new PositionDTO(id,dimensionId, x, y, z, yaw, pitch);
    }

    // -- Create

    /**
     * Create a new positions from the given parameters.
     * @param dimensionId the dimension id where the position is located.
     * @param x the x coordinate.
     * @param y the y coordinate.
     * @param z the z coordinate.
     * @param yaw the yaw orientation.
     * @param pitch the pitch orientation.
     * @return the created {@link PositionDTO}.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public PositionDTO createPosition(@NotNull String dimensionId, double x, double y, double z, float yaw, float pitch) {
        final String sql = """
                INSERT INTO sb_positions (dimension_id, x, y, z, orientation_yaw, orientation_pitch)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        final long id;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, dimensionId);
            stmt.setDouble(2, x);
            stmt.setDouble(3, y);
            stmt.setDouble(4, z);
            stmt.setFloat(5, yaw);
            stmt.setFloat(6, pitch);
            final int affected = stmt.executeUpdate();
            if (affected == 0) throw new RuntimeException("Creating position failed, no rows affected.");
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new RuntimeException("No generated key");
                }
                id = keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creating position", e);
        }
        return getPositionById(id).orElseThrow(() -> new RuntimeException("Created position not found with id: " + id));
    }

    /**
     * Create a new position based on a {@link PositionDTO}.
     * @param position the {@link PositionDTO} to insert into the database.
     * @return the created {@link PositionDTO}.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public PositionDTO createPosition(PositionDTO position) {
        return createPosition(
                position.dimensionId(),
                position.x(),
                position.y(),
                position.z(),
                position.yaw(),
                position.pitch()
        );
    }

    // -- Read

    /**
     * Fetch a position by its unique ID.
     * @param id the unique ID of the position.
     * @return An {@link Optional} containing the {@link PositionDTO} if found, or empty if not found.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public Optional<PositionDTO> getPositionById(long id) {
        final String sql = """
                SELECT dimension_id, x, y, z, orientation_yaw, orientation_pitch
                FROM sb_positions
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapPosition(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching position by id: " + id, e);
        }
    }

    /**
     * Retrieve all positions from the database.
     * @return A list of {@link PositionDTO} objects representing all positions.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public List<PositionDTO> getAllPositions() {
        final String sql = """
                SELECT dimension_id, x, y, z, orientation_yaw, orientation_pitch
                FROM sb_positions
                ORDER BY id ASC
                """;
        final List<PositionDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                out.add(mapPosition(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing positions", e);
        }
    }

    /**
     * Retrieve positions from the database with pagination.
     * @param limit  the maximum number of positions to retrieve.
     * @param offset the number of positions to skip before starting to collect the result set.
     * @return A list of {@link PositionDTO} objects representing the paged positions.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public List<PositionDTO> getAllPositionsPaged(int limit, int offset) {
        final String sql = """
                SELECT dimension_id, x, y, z, orientation_yaw, orientation_pitch
                FROM sb_positions
                ORDER BY id ASC
                LIMIT ? OFFSET ?
                """;
        final List<PositionDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(mapPosition(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing positions paged", e);
        }
    }

    // -- Update

    /**
     * We do not recommend updating positions. Use delete + create instead.
     * If you still want to update a position, you can use this method.
     *
     * @param id       the id of the position to update
     * @param position requires an updated {@link PositionDTO} object.
     * @return An {@link Optional} containing the updated {@link PositionDTO} if the update was successful, or an empty if failed.
     */
    public Optional<PositionDTO> updatePosition(long id, @NotNull PositionDTO position) {
        final Optional<PositionDTO> before = getPositionById(id);
        if (before.isEmpty()) return Optional.empty();
        final String sql = """
                UPDATE sb_positions
                SET dimension_id = ?, x = ?, y = ?, z = ?, orientation_yaw = ?, orientation_pitch = ?
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, position.dimensionId());
            stmt.setDouble(2, position.x());
            stmt.setDouble(3, position.y());
            stmt.setDouble(4, position.z());
            stmt.setFloat(5, position.yaw());
            stmt.setFloat(6, position.pitch());
            stmt.setLong(7, id);
        } catch (SQLException e) {
            throw new RuntimeException("Error updating position id=" + id, e);
        }
        return getPositionById(id);
    }

    // -- Delete

    /**
     * Delete a position by its unique ID.
     * @param id the unique ID of the position to delete.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public void deletePositionById(long id) {
        final String sql = "DELETE FROM sb_positions WHERE id = ?";
        final Optional<PositionDTO> before = getPositionById(id);

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            final int affected = stmt.executeUpdate();
            if (affected == 0) throw new RuntimeException("No position deleted for id: " + id);
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting position id=" + id, e);
        }
    }

    // -- Memory Management
    /**
     * Delete all positions that are not referenced by any other records in the database.
     * @return the number of deleted positions.
     * @throws RuntimeException on SQL errors coming from jdbc.
     */
    public int deleteUnusedPositions() {
        try (Connection conn = db.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            List<java.util.Map.Entry<String, String>> refs = new ArrayList<>();
            try (ResultSet fkRs = meta.getExportedKeys(conn.getCatalog(), null, "sb_positions")) {
                while (fkRs.next()) {
                    String fkTable = fkRs.getString("FKTABLE_NAME");
                    String fkColumn = fkRs.getString("FKCOLUMN_NAME");
                    if (fkTable != null && fkColumn != null) {
                        refs.add(new java.util.AbstractMap.SimpleEntry<>(fkTable, fkColumn));
                    }
                }
            }

            StringBuilder sql = new StringBuilder("DELETE FROM sb_positions");
            if (!refs.isEmpty()) {
                sql.append(" WHERE ");
                for (int i = 0; i < refs.size(); i++) {
                    if (i > 0) sql.append(" AND ");
                    String alias = "t" + i;
                    sql.append("NOT EXISTS (SELECT 1 FROM ")
                            .append(refs.get(i).getKey())
                            .append(" ").append(alias)
                            .append(" WHERE ").append(alias).append(".")
                            .append(refs.get(i).getValue())
                            .append(" = sb_positions.id)");
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                return stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting unused positions", e);
        }
    }

}