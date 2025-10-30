package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.KitDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD manager for {@link KitDTO}.
 * <p>
 * Persists kits in table {@code sb_kits} with columns:
 * <ul>
 *   <li>id INTEGER PRIMARY KEY AUTOINCREMENT</li>
 *   <li>name TEXT NOT NULL UNIQUE</li>
 *   <li>description TEXT</li>
 *   <li>updated_at TEXT NOT NULL DEFAULT (datetime('now'))</li>
 *   <li>cooldown_seconds INTEGER NOT NULL DEFAULT 0</li>
 * </ul>
 * <p>
 * All SQLExceptions are wrapped into RuntimeExceptions with context.
 */
public final class KitsCrudManager {
    private final DatabaseManager db;

    /**
     * Creates a new CRUD manager for kits.
     *
     * @param db Database manager providing JDBC connections.
     */
    public KitsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    /**
     * Binds a {@link KitDTO} to a PreparedStatement in the order used for INSERT/UPDATE:
     * name, description, updated_at, cooldown_seconds.
     */
    private static void bindKitNoId(@NotNull PreparedStatement ps, @NotNull KitDTO k) throws SQLException {
        ps.setString(1, k.name());
        if (k.description() == null) {
            ps.setNull(2, Types.VARCHAR);
        } else {
            ps.setString(2, k.description());
        }
        ps.setString(3, k.updatedAt());
        ps.setInt(4, k.cooldownSeconds());
    }

    /**
     * Maps the current row to {@link KitDTO}.
     */
    private static KitDTO mapKit(@NotNull ResultSet rs) throws SQLException {
        final long id = rs.getLong("id");
        final String name = rs.getString("name");
        final String description = rs.getString("description");
        final String updatedAt = rs.getString("updated_at");
        final int cooldownSeconds = rs.getInt("cooldown_seconds");
        return new KitDTO(id, name, description, updatedAt, cooldownSeconds);
    }

    /**
     * Inserts a new kit row.
     *
     * @param kit Input kit. Its {@code id} value is ignored for insertion.
     * @return Persisted {@link KitDTO} including the generated {@code id}.
     * @throws RuntimeException on SQL errors or if no id is generated.
     */
    public KitDTO createKit(@NotNull KitDTO kit) {
        final String sql = """
                INSERT INTO sb_kits (name, description, updated_at, cooldown_seconds)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindKitNoId(ps, kit);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new RuntimeException("Failed to insert kit: no generated key returned");
                }
                final long id = keys.getLong(1);
                return new KitDTO(id, kit.name(), kit.description(), kit.updatedAt(), kit.cooldownSeconds());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting kit name=" + kit.name(), e);
        }
    }

    /**
     * Retrieves a kit by its database id.
     *
     * @param id Kit id.
     * @return Optional containing the found kit or empty if none.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<KitDTO> getKitById(long id) {
        final String sql = """
                SELECT id, name, description, updated_at, cooldown_seconds
                FROM sb_kits
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapKit(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching kit by id=" + id, e);
        }
    }

    /**
     * Retrieves a kit by its unique name.
     *
     * @param name Unique kit name.
     * @return Optional containing the found kit or empty if none.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<KitDTO> getKitByUniqueName(@NotNull String name) {
        final String sql = """
                SELECT id, name, description, updated_at, cooldown_seconds
                FROM sb_kits
                WHERE name = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapKit(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching kit by unique name=" + name, e);
        }
    }

    /**
     * Lists all kits ordered by id.
     *
     * @return List of {@link KitDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public List<KitDTO> getAllKits() {
        final String sql = """
                SELECT id, name, description, updated_at, cooldown_seconds
                FROM sb_kits
                ORDER BY id ASC
                """;
        final List<KitDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(mapKit(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing kits", e);
        }
    }

    /**
     * Updates an existing kit by id.
     *
     * @param id  Kit id.
     * @param kit New values to set. {@code id} inside the DTO is ignored.
     * @return Optional containing the updated kit or empty if the row did not exist.
     * @throws RuntimeException on SQL errors or unexpected empty fetch after update.
     */
    public Optional<KitDTO> updateKit(long id, @NotNull KitDTO kit) {
        if (getKitById(id).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_kits
                SET name = ?, description = ?, updated_at = ?, cooldown_seconds = ?
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindKitNoId(ps, kit);
            ps.setLong(5, id);
            final int updated = ps.executeUpdate();
            if (updated == 0) throw new RuntimeException("No kit updated for id=" + id);

            return getKitById(id).map(k -> k).or(() -> {
                throw new RuntimeException("Updated kit not found for id=" + id);
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error updating kit id=" + id, e);
        }
    }

    // ------------------------- helpers -------------------------

    /**
     * Deletes a kit by id.
     *
     * @param id Kit id to delete.
     * @return Optional containing the pre-delete kit or empty if it did not exist.
     * @throws RuntimeException on SQL errors or if delete affects zero rows after existence check.
     */
    public Optional<KitDTO> deleteKitById(long id) {
        final Optional<KitDTO> before = getKitById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = "DELETE FROM sb_kits WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            final int affected = ps.executeUpdate();
            if (affected == 0) throw new RuntimeException("No kit deleted for id=" + id);
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting kit id=" + id, e);
        }
    }

    /**
     * Renames a kit by id.
     *
     * @param id      Kit id.
     * @param newName New unique name.
     * @return Optional containing the updated kit or empty if the row did not exist.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<KitDTO> renameKit(long id, @NotNull String newName) {
        final Optional<KitDTO> existing = getKitById(id);
        if (existing.isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_kits
                SET name = ?
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setLong(2, id);
            ps.executeUpdate();
            return getKitById(id);
        } catch (SQLException e) {
            throw new RuntimeException("Error renaming kit id=" + id + " to name=" + newName, e);
        }
    }
}
