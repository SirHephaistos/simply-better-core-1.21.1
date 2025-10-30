package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.PositionDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD manager for {@link PositionDTO}.
 * <p>
 * Persists positions in table {@code sb_positions} with columns:
 * <ul>
 *   <li>id INTEGER PRIMARY KEY AUTOINCREMENT</li>
 *   <li>dimension_id TEXT NOT NULL</li>
 *   <li>x REAL NOT NULL</li>
 *   <li>y REAL NOT NULL</li>
 *   <li>z REAL NOT NULL</li>
 *   <li>orientation_yaw REAL NOT NULL</li>
 *   <li>orientation_pitch REAL NOT NULL</li>
 * </ul>
 * Notes:
 * <ul>
 *   <li>{@code PositionDTO} does not carry the database id. Methods that need it take or return a {@code long id}.</li>
 *   <li>All SQLExceptions are wrapped in RuntimeExceptions with context.</li>
 * </ul>
 */
public final class PositionsCrudManager {
    private final DatabaseManager db;

    /**
     * Creates a new CRUD manager for positions.
     *
     * @param db Database manager providing JDBC connections.
     */
    public PositionsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    /**
     * Binds a {@link PositionDTO} to a PreparedStatement in the order used by this manager.
     * Order: dimension_id, x, y, z, orientation_yaw, orientation_pitch.
     */
    private static void bindPosition(@NotNull PreparedStatement stmt, @NotNull PositionDTO p) throws SQLException {
        stmt.setString(1, p.dimensionId());
        stmt.setDouble(2, p.x());
        stmt.setDouble(3, p.y());
        stmt.setDouble(4, p.z());
        stmt.setFloat(5, p.yaw());
        stmt.setFloat(6, p.pitch());
    }

    /**
     * Maps the current row to {@link PositionDTO}.
     */
    private static PositionDTO mapPosition(@NotNull ResultSet rs) throws SQLException {
        final String dimensionId = rs.getString("dimension_id");
        final double x = rs.getDouble("x");
        final double y = rs.getDouble("y");
        final double z = rs.getDouble("z");
        final float yaw = rs.getFloat("orientation_yaw");
        final float pitch = rs.getFloat("orientation_pitch");
        return new PositionDTO(dimensionId, x, y, z, yaw, pitch);
    }

    /**
     * Inserts a new position row.
     *
     * @param position Position data to persist.
     * @return Generated database id.
     * @throws RuntimeException on SQL errors or if no id is generated.
     */
    public long createPosition(@NotNull PositionDTO position) {
        final String sql = """
                INSERT INTO sb_positions (dimension_id, x, y, z, orientation_yaw, orientation_pitch)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindPosition(stmt, position);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new RuntimeException("Failed to insert position: no generated key returned");
                }
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting position", e);
        }
    }

    /**
     * Retrieves a position by id.
     *
     * @param id Database id.
     * @return Optional containing the found {@link PositionDTO}, empty if none.
     * @throws RuntimeException on SQL errors.
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
     * Lists all positions.
     *
     * @return List of {@link PositionDTO}.
     * @throws RuntimeException on SQL errors.
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
     * Updates an existing position row by id.
     *
     * @param id       Database id to update.
     * @param position New position values.
     * @return Optional containing the updated {@link PositionDTO}, empty if the row did not exist.
     * @throws RuntimeException on SQL errors or unexpected empty fetch after update.
     */
    public Optional<PositionDTO> updatePosition(long id, @NotNull PositionDTO position) {
        // Check existence first to return Optional.empty() without throwing.
        if (getPositionById(id).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_positions
                SET dimension_id = ?, x = ?, y = ?, z = ?, orientation_yaw = ?, orientation_pitch = ?
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindPosition(stmt, position);
            stmt.setLong(7, id);
            final int updated = stmt.executeUpdate();
            if (updated == 0) throw new RuntimeException("No position updated for id: " + id);

            // Re-fetch to return the new state
            return getPositionById(id).map(p -> p).or(() -> {
                throw new RuntimeException("Updated position not found for id: " + id);
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error updating position id=" + id, e);
        }
    }

    /**
     * Deletes a position by id.
     *
     * @param id Database id to delete.
     * @return Optional containing the pre-delete {@link PositionDTO}, empty if it did not exist.
     * @throws RuntimeException on SQL errors or if delete affects zero rows after existence check.
     */
    public Optional<PositionDTO> deletePositionById(long id) {
        final String sql = "DELETE FROM sb_positions WHERE id = ?";
        final Optional<PositionDTO> before = getPositionById(id);
        if (before.isEmpty()) return Optional.empty();

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            final int affected = stmt.executeUpdate();
            if (affected == 0) throw new RuntimeException("No position deleted for id: " + id);
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting position id=" + id, e);
        }
    }

    // ------------------------- helpers -------------------------

    /**
     * Lists positions within the same dimension.
     *
     * @param dimensionId Namespaced dimension id, e.g. {@code minecraft:overworld}.
     * @return List of matching positions.
     * @throws RuntimeException on SQL errors.
     */
    public List<PositionDTO> getPositionsByDimension(@NotNull String dimensionId) {
        final String sql = """
                SELECT dimension_id, x, y, z, orientation_yaw, orientation_pitch
                FROM sb_positions
                WHERE dimension_id = ?
                ORDER BY id ASC
                """;
        final List<PositionDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dimensionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) out.add(mapPosition(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing positions for dimension=" + dimensionId, e);
        }
    }

    /**
     * Finds the id of a position that exactly matches the provided coordinates and orientation.
     * Useful to de-duplicate positions before inserting.
     *
     * @param position Position to match exactly.
     * @return Optional id if found.
     * @throws RuntimeException on SQL errors.
     *                          // TODO: If floating-point tolerances are desired, replace equality by range comparisons.
     */
    public Optional<Long> findExactPositionId(@NotNull PositionDTO position) {
        final String sql = """
                SELECT id
                FROM sb_positions
                WHERE dimension_id = ?
                  AND x = ?
                  AND y = ?
                  AND z = ?
                  AND orientation_yaw = ?
                  AND orientation_pitch = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindPosition(stmt, position);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(rs.getLong("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error searching exact position", e);
        }
    }
}
