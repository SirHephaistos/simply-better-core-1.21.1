package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.IgnoreDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_ignores (player-to-player ignores).
 * Composite PK: (owner_uuid, target_uuid).
 */
public final class IgnoresCrudManager {
    private final DatabaseManager db;

    public IgnoresCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static IgnoreDTO mapIgnore(ResultSet rs) throws SQLException {
        final String owner = rs.getString("i_owner_uuid");
        final String target = rs.getString("i_target_uuid");
        final String createdAt = rs.getString("i_created_at");
        return new IgnoreDTO(owner, target, createdAt);
    }

    /**
     * Insert a new ignore row using DB default for created_at.
     */
    public IgnoreDTO createIgnore(@NotNull String ownerUuid, @NotNull String targetUuid) {
        final String sql = """
                INSERT INTO sb_ignores (owner_uuid, target_uuid)
                VALUES (?, ?)
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            ps.setString(2, targetUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("createIgnore failed for owner=" + ownerUuid + " target=" + targetUuid, e);
        }
        // fetch inserted row
        return getIgnore(ownerUuid, targetUuid)
                .orElseThrow(() -> new RuntimeException("createIgnore post-fetch missing for owner=" + ownerUuid + " target=" + targetUuid));
    }

    // -- Read

    /**
     * Insert a new ignore row with explicit created_at. ISO-8601 string recommended.
     */
    public IgnoreDTO createIgnore(@NotNull String ownerUuid, @NotNull String targetUuid, @NotNull String createdAt) {
        final String sql = """
                INSERT INTO sb_ignores (owner_uuid, target_uuid, created_at)
                VALUES (?, ?, ?)
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            ps.setString(2, targetUuid);
            ps.setString(3, createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("createIgnore(created_at) failed for owner=" + ownerUuid + " target=" + targetUuid, e);
        }
        return getIgnore(ownerUuid, targetUuid)
                .orElseThrow(() -> new RuntimeException("createIgnore(created_at) post-fetch missing for owner=" + ownerUuid + " target=" + targetUuid));
    }

    /**
     * Get one ignore by composite key.
     */
    public Optional<IgnoreDTO> getIgnore(@NotNull String ownerUuid, @NotNull String targetUuid) {
        final String sql = """
                SELECT
                    i.owner_uuid  AS i_owner_uuid,
                    i.target_uuid AS i_target_uuid,
                    i.created_at  AS i_created_at
                FROM sb_ignores i
                WHERE i.owner_uuid = ? AND i.target_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            ps.setString(2, targetUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapIgnore(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getIgnore failed for owner=" + ownerUuid + " target=" + targetUuid, e);
        }
    }

    /**
     * List all ignores.
     */
    public List<IgnoreDTO> getAllIgnores() {
        final String sql = """
                SELECT
                    i.owner_uuid  AS i_owner_uuid,
                    i.target_uuid AS i_target_uuid,
                    i.created_at  AS i_created_at
                FROM sb_ignores i
                ORDER BY i.owner_uuid, i.target_uuid
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<IgnoreDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapIgnore(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllIgnores failed", e);
        }
    }

    /**
     * List ignores by owner.
     */
    public List<IgnoreDTO> getIgnoresByOwner(@NotNull String ownerUuid) {
        final String sql = """
                SELECT
                    i.owner_uuid  AS i_owner_uuid,
                    i.target_uuid AS i_target_uuid,
                    i.created_at  AS i_created_at
                FROM sb_ignores i
                WHERE i.owner_uuid = ?
                ORDER BY i.target_uuid
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<IgnoreDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapIgnore(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getIgnoresByOwner failed for owner=" + ownerUuid, e);
        }
    }

    /**
     * List ignores by target.
     */
    public List<IgnoreDTO> getIgnoresByTarget(@NotNull String targetUuid) {
        final String sql = """
                SELECT
                    i.owner_uuid  AS i_owner_uuid,
                    i.target_uuid AS i_target_uuid,
                    i.created_at  AS i_created_at
                FROM sb_ignores i
                WHERE i.target_uuid = ?
                ORDER BY i.owner_uuid
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, targetUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<IgnoreDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapIgnore(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getIgnoresByTarget failed for target=" + targetUuid, e);
        }
    }

    // -- Update
    // No mutable fields besides created_at, which should rarely change.
    // Provide explicit setter if needed.

    /**
     * Convenience: check if owner ignores target.
     */
    public boolean existsIgnore(@NotNull String ownerUuid, @NotNull String targetUuid) {
        final String sql = """
                SELECT 1
                FROM sb_ignores i
                WHERE i.owner_uuid = ? AND i.target_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            ps.setString(2, targetUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("existsIgnore failed for owner=" + ownerUuid + " target=" + targetUuid, e);
        }
    }

    // -- Delete

    /**
     * Update created_at.
     */
    public IgnoreDTO updateCreatedAt(@NotNull String ownerUuid, @NotNull String targetUuid, @NotNull String createdAt) {
        final String sql = """
                UPDATE sb_ignores
                SET created_at = ?
                WHERE owner_uuid = ? AND target_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, createdAt);
            ps.setString(2, ownerUuid);
            ps.setString(3, targetUuid);
            final int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new RuntimeException("updateCreatedAt affected 0 rows for owner=" + ownerUuid + " target=" + targetUuid);
            }
        } catch (SQLException e) {
            throw new RuntimeException("updateCreatedAt failed for owner=" + ownerUuid + " target=" + targetUuid, e);
        }
        return getIgnore(ownerUuid, targetUuid)
                .orElseThrow(() -> new RuntimeException("updateCreatedAt post-fetch missing for owner=" + ownerUuid + " target=" + targetUuid));
    }

    // -- Mapper

    /**
     * Delete and return the removed row if it existed.
     */
    public Optional<IgnoreDTO> deleteIgnore(@NotNull String ownerUuid, @NotNull String targetUuid) {
        final Optional<IgnoreDTO> before = getIgnore(ownerUuid, targetUuid);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_ignores
                WHERE owner_uuid = ? AND target_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            ps.setString(2, targetUuid);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteIgnore failed for owner=" + ownerUuid + " target=" + targetUuid, e);
        }
    }
}
