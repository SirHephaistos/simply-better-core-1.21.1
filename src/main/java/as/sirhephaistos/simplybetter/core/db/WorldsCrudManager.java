package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.WorldDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD manager for {@link WorldDTO}.
 * <p>
 * Persists worlds in table {@code sb_worlds} with columns:
 * <ul>
 *   <li>id INTEGER PRIMARY KEY AUTOINCREMENT</li>
 *   <li>dimension_id TEXT NOT NULL UNIQUE</li>
 *   <li>center_position_id INTEGER NULL REFERENCES sb_positions(id) ON DELETE SET NULL</li>
 * </ul>
 * All SQLExceptions are wrapped into RuntimeExceptions with context.
 */
public final class WorldsCrudManager {
    private final DatabaseManager db;

    /**
     * Creates a new CRUD manager for worlds.
     *
     * @param db Database manager providing JDBC connections.
     */
    public WorldsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    /**
     * Maps the current row to {@link WorldDTO}.
     */
    private static WorldDTO mapWorld(@NotNull ResultSet rs) throws SQLException {
        final long id = rs.getLong("id");
        final String dimensionId = rs.getString("dimension_id");
        final long centerId = rs.getLong("center_position_id");
        final Long centerNullable = rs.wasNull() ? null : centerId;
        return new WorldDTO(id, dimensionId, centerNullable);
    }

    /**
     * Inserts a new world row.
     *
     * @param world Input world. Its {@code id} is ignored for insertion.
     * @return Persisted {@link WorldDTO} including generated {@code id}.
     * @throws RuntimeException on SQL errors or if no id is generated.
     */
    public WorldDTO createWorld(@NotNull WorldDTO world) {
        final String sql = """
                INSERT INTO sb_worlds (dimension_id, center_position_id)
                VALUES (?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, world.dimensionId());
            if (world.centerPositionId() == null) {
                ps.setNull(2, Types.BIGINT);
            } else {
                ps.setLong(2, world.centerPositionId());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new RuntimeException("Failed to insert world: no generated key returned");
                }
                final long id = keys.getLong(1);
                return new WorldDTO(id, world.dimensionId(), world.centerPositionId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting world for dimension=" + world.dimensionId(), e);
        }
    }

    /**
     * Retrieves a world by its database id.
     *
     * @param id World id.
     * @return Optional containing the found world or empty if none.
     * @throws RuntimeException on SQL errors.
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
     * Retrieves a world by its unique {@code dimension_id}.
     *
     * @param dimensionId Namespaced dimension id, e.g. {@code minecraft:overworld}.
     * @return Optional containing the found world or empty if none.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<WorldDTO> getWorldByUniqueDimensionId(@NotNull String dimensionId) {
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
     * Lists all worlds ordered by id.
     *
     * @return List of {@link WorldDTO}.
     * @throws RuntimeException on SQL errors.
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
     * Updates an existing world by id.
     *
     * @param id    World id.
     * @param world New values to set. {@code id} inside the DTO is ignored.
     * @return Optional containing the updated world or empty if the row did not exist.
     * @throws RuntimeException on SQL errors or unexpected empty fetch after update.
     */
    public Optional<WorldDTO> updateWorld(long id, @NotNull WorldDTO world) {
        if (getWorldById(id).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_worlds
                SET dimension_id = ?, center_position_id = ?
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world.dimensionId());
            if (world.centerPositionId() == null) {
                ps.setNull(2, Types.BIGINT);
            } else {
                ps.setLong(2, world.centerPositionId());
            }
            ps.setLong(3, id);
            final int updated = ps.executeUpdate();
            if (updated == 0) throw new RuntimeException("No world updated for id=" + id);

            return getWorldById(id).map(w -> w).or(() -> {
                throw new RuntimeException("Updated world not found for id=" + id);
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error updating world id=" + id, e);
        }
    }

    /**
     * Deletes a world by id.
     *
     * @param id World id to delete.
     * @return Optional containing the pre-delete world or empty if it did not exist.
     * @throws RuntimeException on SQL errors or if delete affects zero rows after existence check.
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

    // ------------------------- helpers -------------------------

    /**
     * Sets the center position foreign key for a world.
     *
     * @param id         World id.
     * @param positionId Position id to set. Use {@code null} to clear.
     * @return Optional containing the updated world or empty if the row did not exist.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<WorldDTO> setCenterPosition(long id, Long positionId) {
        if (getWorldById(id).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_worlds
                SET center_position_id = ?
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (positionId == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, positionId);
            }
            ps.setLong(2, id);
            ps.executeUpdate();
            return getWorldById(id);
        } catch (SQLException e) {
            throw new RuntimeException("Error setting center_position_id for world id=" + id, e);
        }
    }
}
