package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.JailSanctionDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_jail_sanctions.
 * Columns inferred from JailSanctionDTO:
 * id BIGINT AUTO_INCREMENT PRIMARY KEY,
 * created_at TEXT NOT NULL,
 * expires_at TEXT NULL,
 * reason TEXT NOT NULL,
 * player_uuid TEXT NOT NULL,
 * sanctioned_by_uuid TEXT NOT NULL,
 * jail_id BIGINT NOT NULL
 * TODO: switch TEXT to TIMESTAMP if schema uses real timestamps.
 * TODO: add FK sb_jail_sanctions.jail_id -> sb_jails(id), indexes on player_uuid, jail_id, expires_at.
 */
public final class JailSanctionsCrudManager {
    private final DatabaseManager db;

    public JailSanctionsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static JailSanctionDTO mapJailSanction(ResultSet rs) throws SQLException {
        final long id = rs.getLong("js_id");
        final String createdAt = rs.getString("js_created_at");
        final String expiresAt = rs.getString("js_expires_at"); // may be null
        final String reason = rs.getString("js_reason");
        final String playerUuid = rs.getString("js_player_uuid");
        final String sanctionedByUuid = rs.getString("js_sanctioned_by_uuid");
        final long jailId = rs.getLong("js_jail_id");
        return new JailSanctionDTO(id, createdAt, expiresAt, reason, playerUuid, sanctionedByUuid, jailId);
    }

    // -- Read

    /**
     * Insert a new jail sanction. Returns the persisted row.
     */
    public JailSanctionDTO createJailSanction(@NotNull String createdAt,
                                              String expiresAt,
                                              @NotNull String reason,
                                              @NotNull String playerUuid,
                                              @NotNull String sanctionedByUuid,
                                              long jailId) {
        final String sql = """
                INSERT INTO sb_jail_sanctions (created_at, expires_at, reason, player_uuid, sanctioned_by_uuid, jail_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        long id;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, createdAt);
            if (expiresAt == null) ps.setNull(2, Types.VARCHAR);
            else ps.setString(2, expiresAt);
            ps.setString(3, reason);
            ps.setString(4, playerUuid);
            ps.setString(5, sanctionedByUuid);
            ps.setLong(6, jailId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("createJailSanction: no generated key");
                id = keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createJailSanction failed for player=" + playerUuid, e);
        }
        return getJailSanctionById(id)
                .orElseThrow(() -> new RuntimeException("createJailSanction post-fetch missing id=" + id));
    }

    /**
     * Get by id.
     */
    public Optional<JailSanctionDTO> getJailSanctionById(long id) {
        final String sql = """
                SELECT
                    js.id                 AS js_id,
                    js.created_at         AS js_created_at,
                    js.expires_at         AS js_expires_at,
                    js.reason             AS js_reason,
                    js.player_uuid        AS js_player_uuid,
                    js.sanctioned_by_uuid AS js_sanctioned_by_uuid,
                    js.jail_id            AS js_jail_id
                FROM sb_jail_sanctions js
                WHERE js.id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapJailSanction(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getJailSanctionById failed id=" + id, e);
        }
    }

    /**
     * List all jail sanctions.
     */
    public List<JailSanctionDTO> getAllJailSanctions() {
        final String sql = """
                SELECT
                    js.id                 AS js_id,
                    js.created_at         AS js_created_at,
                    js.expires_at         AS js_expires_at,
                    js.reason             AS js_reason,
                    js.player_uuid        AS js_player_uuid,
                    js.sanctioned_by_uuid AS js_sanctioned_by_uuid,
                    js.jail_id            AS js_jail_id
                FROM sb_jail_sanctions js
                ORDER BY js.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<JailSanctionDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapJailSanction(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllJailSanctions failed", e);
        }
    }

    /**
     * List sanctions by player.
     */
    public List<JailSanctionDTO> getJailSanctionsByPlayerUuid(@NotNull String playerUuid) {
        final String sql = """
                SELECT
                    js.id                 AS js_id,
                    js.created_at         AS js_created_at,
                    js.expires_at         AS js_expires_at,
                    js.reason             AS js_reason,
                    js.player_uuid        AS js_player_uuid,
                    js.sanctioned_by_uuid AS js_sanctioned_by_uuid,
                    js.jail_id            AS js_jail_id
                FROM sb_jail_sanctions js
                WHERE js.player_uuid = ?
                ORDER BY js.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<JailSanctionDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapJailSanction(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getJailSanctionsByPlayerUuid failed for player=" + playerUuid, e);
        }
    }

    /**
     * List sanctions by jail id.
     */
    public List<JailSanctionDTO> getJailSanctionsByJailId(long jailId) {
        final String sql = """
                SELECT
                    js.id                 AS js_id,
                    js.created_at         AS js_created_at,
                    js.expires_at         AS js_expires_at,
                    js.reason             AS js_reason,
                    js.player_uuid        AS js_player_uuid,
                    js.sanctioned_by_uuid AS js_sanctioned_by_uuid,
                    js.jail_id            AS js_jail_id
                FROM sb_jail_sanctions js
                WHERE js.jail_id = ?
                ORDER BY js.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, jailId);
            try (ResultSet rs = ps.executeQuery()) {
                final List<JailSanctionDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapJailSanction(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getJailSanctionsByJailId failed for jailId=" + jailId, e);
        }
    }

    // -- Update

    /**
     * List currently effective sanctions for a player, comparing ISO-8601 strings.
     * Pass nowIso as the current timestamp string.
     * TODO: If the schema uses TIMESTAMP, compare using DB functions instead.
     */
    public List<JailSanctionDTO> getActiveJailSanctionsByPlayerUuid(@NotNull String playerUuid, @NotNull String nowIso) {
        final String sql = """
                SELECT
                    js.id                 AS js_id,
                    js.created_at         AS js_created_at,
                    js.expires_at         AS js_expires_at,
                    js.reason             AS js_reason,
                    js.player_uuid        AS js_player_uuid,
                    js.sanctioned_by_uuid AS js_sanctioned_by_uuid,
                    js.jail_id            AS js_jail_id
                FROM sb_jail_sanctions js
                WHERE js.player_uuid = ?
                  AND (js.expires_at IS NULL OR js.expires_at > ?)
                ORDER BY js.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setString(2, nowIso);
            try (ResultSet rs = ps.executeQuery()) {
                final List<JailSanctionDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapJailSanction(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getActiveJailSanctionsByPlayerUuid failed for player=" + playerUuid, e);
        }
    }

    /**
     * Update reason, expiresAt, and jailId.
     */
    public JailSanctionDTO updateJailSanction(long id, @NotNull String reason, String expiresAt, long jailId) {
        final String sql = """
                UPDATE sb_jail_sanctions
                SET reason = ?, expires_at = ?, jail_id = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reason);
            if (expiresAt == null) ps.setNull(2, Types.VARCHAR);
            else ps.setString(2, expiresAt);
            ps.setLong(3, jailId);
            ps.setLong(4, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("updateJailSanction affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("updateJailSanction failed id=" + id, e);
        }
        return getJailSanctionById(id)
                .orElseThrow(() -> new RuntimeException("updateJailSanction post-fetch missing id=" + id));
    }

    /**
     * Convenience: update only expiry.
     */
    public JailSanctionDTO setJailSanctionExpiry(long id, String expiresAt) {
        final String sql = """
                UPDATE sb_jail_sanctions
                SET expires_at = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (expiresAt == null) ps.setNull(1, Types.VARCHAR);
            else ps.setString(1, expiresAt);
            ps.setLong(2, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setJailSanctionExpiry affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("setJailSanctionExpiry failed id=" + id, e);
        }
        return getJailSanctionById(id)
                .orElseThrow(() -> new RuntimeException("setJailSanctionExpiry post-fetch missing id=" + id));
    }

    // -- Delete

    /**
     * Convenience: move sanction to a different jail.
     */
    public JailSanctionDTO moveJailSanction(long id, long jailId) {
        final String sql = """
                UPDATE sb_jail_sanctions
                SET jail_id = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, jailId);
            ps.setLong(2, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("moveJailSanction affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("moveJailSanction failed id=" + id, e);
        }
        return getJailSanctionById(id)
                .orElseThrow(() -> new RuntimeException("moveJailSanction post-fetch missing id=" + id));
    }

    // -- Mapper

    /**
     * Delete by id and return prior row if it existed.
     */
    public Optional<JailSanctionDTO> deleteJailSanctionById(long id) {
        final Optional<JailSanctionDTO> before = getJailSanctionById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_jail_sanctions
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteJailSanctionById failed id=" + id, e);
        }
    }
}
