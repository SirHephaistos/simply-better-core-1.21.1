package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.HomeDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD manager for {@link HomeDTO}.
 * <p>
 * Persists homes in table {@code sb_homes} with columns:
 * <ul>
 *   <li>id INTEGER PRIMARY KEY AUTOINCREMENT</li>
 *   <li>name TEXT NOT NULL</li>
 *   <li>created_at TEXT NOT NULL DEFAULT (datetime('now'))</li>
 *   <li>owner_uuid TEXT NOT NULL REFERENCES sb_players(uuid) ON DELETE CASCADE</li>
 *   <li>position_id INTEGER NOT NULL REFERENCES sb_positions(id) ON DELETE CASCADE</li>
 * </ul>
 * Uniqueness:
 * <ul>
 *   <li>UNIQUE(owner_uuid, name)</li>
 *   <li>UNIQUE(owner_uuid, position_id)</li>
 * </ul>
 * All SQLExceptions are wrapped in RuntimeExceptions with context.
 */
public final class HomesCrudManager {
    private final DatabaseManager db;

    /**
     * Creates a new CRUD manager for homes.
     *
     * @param db Database manager providing JDBC connections.
     */
    public HomesCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    private static long requireId(Long id, String field) {
        if (id == null) throw new IllegalArgumentException(field + " must not be null");
        return id;
    }

    private static HomeDTO map(@NotNull ResultSet rs) throws SQLException {
        final long id = rs.getLong("id");
        final String name = rs.getString("name");
        final String createdAt = rs.getString("created_at");
        final String ownerUuid = rs.getString("owner_uuid");
        final long posId = rs.getLong("position_id");
        return new HomeDTO(id, name, createdAt, ownerUuid, posId);
    }

    /**
     * Inserts a new home row.
     *
     * @param h Input home. {@code id} is ignored for insertion.
     * @return Persisted {@link HomeDTO} including the generated {@code id}.
     * @throws RuntimeException on SQL errors or if no id is generated.
     */
    public HomeDTO createHome(@NotNull HomeDTO h) {
        // created_at: if null, use DB default now()
        final String sql = """
                INSERT INTO sb_homes (name, created_at, owner_uuid, position_id)
                VALUES (?, COALESCE(?, datetime('now')), ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, h.name());
            if (h.createdAt() == null) {
                ps.setNull(2, Types.VARCHAR);
            } else {
                ps.setString(2, h.createdAt());
            }
            ps.setString(3, h.ownerUuid());
            ps.setLong(4, requireId(h.positionId(), "positionId"));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("Failed to insert home: no generated key returned");
                final long id = keys.getLong(1);
                // If created_at was null, re-read to get actual timestamp
                if (h.createdAt() == null) {
                    return getHomeById(id).orElseThrow(() -> new RuntimeException("Inserted home not found id=" + id));
                }
                return new HomeDTO(id, h.name(), h.createdAt(), h.ownerUuid(), h.positionId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting home owner=" + h.ownerUuid() + " name=" + h.name(), e);
        }
    }

    /**
     * Retrieves a home by database id.
     *
     * @param id Home id.
     * @return Optional with the found {@link HomeDTO}, empty if none.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<HomeDTO> getHomeById(long id) {
        final String sql = """
                SELECT id, name, created_at, owner_uuid, position_id
                FROM sb_homes
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching home by id=" + id, e);
        }
    }

    /**
     * Retrieves a home by its unique owner and name.
     *
     * @param ownerUuid Player UUID.
     * @param name      Home name.
     * @return Optional with the found home, empty if none.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<HomeDTO> getHomeByUniqueOwnerAndName(@NotNull String ownerUuid, @NotNull String name) {
        final String sql = """
                SELECT id, name, created_at, owner_uuid, position_id
                FROM sb_homes
                WHERE owner_uuid = ? AND name = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching home by owner+name owner=" + ownerUuid + " name=" + name, e);
        }
    }

    /**
     * Retrieves a home by its unique owner and position id.
     *
     * @param ownerUuid  Player UUID.
     * @param positionId Position id.
     * @return Optional with the found home, empty if none.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<HomeDTO> getHomeByUniqueOwnerAndPosition(@NotNull String ownerUuid, long positionId) {
        final String sql = """
                SELECT id, name, created_at, owner_uuid, position_id
                FROM sb_homes
                WHERE owner_uuid = ? AND position_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            ps.setLong(2, positionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching home by owner+position owner=" + ownerUuid + " posId=" + positionId, e);
        }
    }

    /**
     * Lists all homes for a player ordered by name.
     *
     * @param ownerUuid Player UUID.
     * @return List of {@link HomeDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public List<HomeDTO> getHomesByOwner(@NotNull String ownerUuid) {
        final String sql = """
                SELECT id, name, created_at, owner_uuid, position_id
                FROM sb_homes
                WHERE owner_uuid = ?
                ORDER BY name ASC
                """;
        final List<HomeDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing homes for owner=" + ownerUuid, e);
        }
    }

    /**
     * Lists all homes.
     *
     * @return List of {@link HomeDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public List<HomeDTO> getAllHomes() {
        final String sql = """
                SELECT id, name, created_at, owner_uuid, position_id
                FROM sb_homes
                ORDER BY id ASC
                """;
        final List<HomeDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing homes", e);
        }
    }

    /**
     * Updates an existing home by id.
     *
     * @param id Home id.
     * @param h  New values to set. {@code id} inside the DTO is ignored.
     * @return Optional with the updated home or empty if the row did not exist.
     * @throws RuntimeException on SQL errors or unexpected empty fetch after update.
     */
    public Optional<HomeDTO> updateHome(long id, @NotNull HomeDTO h) {
        if (getHomeById(id).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_homes
                SET name = ?, created_at = ?, owner_uuid = ?, position_id = ?
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, h.name());
            ps.setString(2, h.createdAt());
            ps.setString(3, h.ownerUuid());
            ps.setLong(4, requireId(h.positionId(), "positionId"));
            ps.setLong(5, id);
            final int updated = ps.executeUpdate();
            if (updated == 0) throw new RuntimeException("No home updated for id=" + id);

            return getHomeById(id).map(x -> x).or(() -> {
                throw new RuntimeException("Updated home not found for id=" + id);
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error updating home id=" + id, e);
        }
    }

    /**
     * Renames a home by id.
     *
     * @param id      Home id.
     * @param newName New unique name per owner.
     * @return Optional with the updated home or empty if the row did not exist.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<HomeDTO> renameHome(long id, @NotNull String newName) {
        if (getHomeById(id).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_homes
                SET name = ?
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setLong(2, id);
            ps.executeUpdate();
            return getHomeById(id);
        } catch (SQLException e) {
            throw new RuntimeException("Error renaming home id=" + id + " to name=" + newName, e);
        }
    }

    // ------------------------- helpers -------------------------

    /**
     * Changes the position of a home by id.
     *
     * @param id            Home id.
     * @param newPositionId New {@code sb_positions.id}.
     * @return Optional with the updated home or empty if the row did not exist.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<HomeDTO> moveHome(long id, long newPositionId) {
        if (getHomeById(id).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_homes
                SET position_id = ?
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, newPositionId);
            ps.setLong(2, id);
            ps.executeUpdate();
            return getHomeById(id);
        } catch (SQLException e) {
            throw new RuntimeException("Error moving home id=" + id + " to position_id=" + newPositionId, e);
        }
    }

    /**
     * Deletes a home by id.
     *
     * @param id Home id to delete.
     * @return Optional containing the pre-delete home or empty if it did not exist.
     * @throws RuntimeException on SQL errors or if delete affects zero rows after existence check.
     */
    public Optional<HomeDTO> deleteHomeById(long id) {
        final Optional<HomeDTO> before = getHomeById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = "DELETE FROM sb_homes WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            final int affected = ps.executeUpdate();
            if (affected == 0) throw new RuntimeException("No home deleted for id=" + id);
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting home id=" + id, e);
        }
    }
}
