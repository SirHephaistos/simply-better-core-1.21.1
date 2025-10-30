package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.KitCooldownDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_kits_cooldowns.
 * Composite PK: (kit_id, player_uuid).
 * Columns:
 * kit_id BIGINT NOT NULL,
 * player_uuid TEXT NOT NULL,
 * started_at TEXT NOT NULL
 * // TODO: add FK sb_kits_cooldowns.kit_id -> sb_kits(id) and index on (player_uuid, kit_id).
 */
public final class KitCooldownsCrudManager {
    private final DatabaseManager db;

    public KitCooldownsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static KitCooldownDTO mapCooldown(ResultSet rs) throws SQLException {
        final long kitId = rs.getLong("kc_kit_id");
        final String playerUuid = rs.getString("kc_player_uuid");
        final String startedAt = rs.getString("kc_started_at");
        return new KitCooldownDTO(kitId, playerUuid, startedAt);
    }

    /**
     * Insert a new cooldown row.
     */
    public KitCooldownDTO createKitCooldown(long kitId, @NotNull String playerUuid, @NotNull String startedAt) {
        final String sql = """
                INSERT INTO sb_kits_cooldowns (kit_id, player_uuid, started_at)
                VALUES (?, ?, ?)
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, kitId);
            ps.setString(2, playerUuid);
            ps.setString(3, startedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("createKitCooldown failed for kitId=" + kitId + " player=" + playerUuid, e);
        }
        return getKitCooldown(kitId, playerUuid)
                .orElseThrow(() -> new RuntimeException("createKitCooldown post-fetch missing for kitId=" + kitId + " player=" + playerUuid));
    }

    // -- Read

    /**
     * Upsert convenience.
     */
    public KitCooldownDTO createOrUpdateKitCooldown(long kitId, @NotNull String playerUuid, @NotNull String startedAt) {
        final Optional<KitCooldownDTO> exists = getKitCooldown(kitId, playerUuid);
        if (exists.isEmpty()) return createKitCooldown(kitId, playerUuid, startedAt);
        return updateStartedAt(kitId, playerUuid, startedAt);
    }

    /**
     * Get cooldown by composite key.
     */
    public Optional<KitCooldownDTO> getKitCooldown(long kitId, @NotNull String playerUuid) {
        final String sql = """
                SELECT
                    kc.kit_id     AS kc_kit_id,
                    kc.player_uuid AS kc_player_uuid,
                    kc.started_at AS kc_started_at
                FROM sb_kits_cooldowns kc
                WHERE kc.kit_id = ? AND kc.player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, kitId);
            ps.setString(2, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapCooldown(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getKitCooldown failed for kitId=" + kitId + " player=" + playerUuid, e);
        }
    }

    /**
     * List all cooldowns.
     */
    public List<KitCooldownDTO> getAllKitCooldowns() {
        final String sql = """
                SELECT
                    kc.kit_id      AS kc_kit_id,
                    kc.player_uuid AS kc_player_uuid,
                    kc.started_at  AS kc_started_at
                FROM sb_kits_cooldowns kc
                ORDER BY kc.kit_id, kc.player_uuid
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<KitCooldownDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapCooldown(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllKitCooldowns failed", e);
        }
    }

    /**
     * List cooldowns for a given kit.
     */
    public List<KitCooldownDTO> getKitCooldownsByKitId(long kitId) {
        final String sql = """
                SELECT
                    kc.kit_id      AS kc_kit_id,
                    kc.player_uuid AS kc_player_uuid,
                    kc.started_at  AS kc_started_at
                FROM sb_kits_cooldowns kc
                WHERE kc.kit_id = ?
                ORDER BY kc.player_uuid
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, kitId);
            try (ResultSet rs = ps.executeQuery()) {
                final List<KitCooldownDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapCooldown(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getKitCooldownsByKitId failed for kitId=" + kitId, e);
        }
    }

    /**
     * List cooldowns for a given player.
     */
    public List<KitCooldownDTO> getKitCooldownsByPlayerUuid(@NotNull String playerUuid) {
        final String sql = """
                SELECT
                    kc.kit_id      AS kc_kit_id,
                    kc.player_uuid AS kc_player_uuid,
                    kc.started_at  AS kc_started_at
                FROM sb_kits_cooldowns kc
                WHERE kc.player_uuid = ?
                ORDER BY kc.kit_id
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<KitCooldownDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapCooldown(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getKitCooldownsByPlayerUuid failed for player=" + playerUuid, e);
        }
    }

    // -- Update

    /**
     * Exists check.
     */
    public boolean existsKitCooldown(long kitId, @NotNull String playerUuid) {
        final String sql = """
                SELECT 1
                FROM sb_kits_cooldowns
                WHERE kit_id = ? AND player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, kitId);
            ps.setString(2, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("existsKitCooldown failed for kitId=" + kitId + " player=" + playerUuid, e);
        }
    }

    // -- Delete

    /**
     * Update started_at.
     */
    public KitCooldownDTO updateStartedAt(long kitId, @NotNull String playerUuid, @NotNull String startedAt) {
        final String sql = """
                UPDATE sb_kits_cooldowns
                SET started_at = ?
                WHERE kit_id = ? AND player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, startedAt);
            ps.setLong(2, kitId);
            ps.setString(3, playerUuid);
            final int upd = ps.executeUpdate();
            if (upd == 0)
                throw new RuntimeException("updateStartedAt affected 0 rows for kitId=" + kitId + " player=" + playerUuid);
        } catch (SQLException e) {
            throw new RuntimeException("updateStartedAt failed for kitId=" + kitId + " player=" + playerUuid, e);
        }
        return getKitCooldown(kitId, playerUuid)
                .orElseThrow(() -> new RuntimeException("updateStartedAt post-fetch missing for kitId=" + kitId + " player=" + playerUuid));
    }

    /**
     * Delete one cooldown and return the prior row if it existed.
     */
    public Optional<KitCooldownDTO> deleteKitCooldown(long kitId, @NotNull String playerUuid) {
        final Optional<KitCooldownDTO> before = getKitCooldown(kitId, playerUuid);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_kits_cooldowns
                WHERE kit_id = ? AND player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, kitId);
            ps.setString(2, playerUuid);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteKitCooldown failed for kitId=" + kitId + " player=" + playerUuid, e);
        }
    }

    /**
     * Bulk delete by kit id. Returns affected count.
     */
    public int deleteKitCooldownsByKitId(long kitId) {
        final String sql = """
                DELETE FROM sb_kits_cooldowns
                WHERE kit_id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, kitId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("deleteKitCooldownsByKitId failed for kitId=" + kitId, e);
        }
    }

    // -- Mapper

    /**
     * Bulk delete by player uuid. Returns affected count.
     */
    public int deleteKitCooldownsByPlayerUuid(@NotNull String playerUuid) {
        final String sql = """
                DELETE FROM sb_kits_cooldowns
                WHERE player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("deleteKitCooldownsByPlayerUuid failed for player=" + playerUuid, e);
        }
    }
}
