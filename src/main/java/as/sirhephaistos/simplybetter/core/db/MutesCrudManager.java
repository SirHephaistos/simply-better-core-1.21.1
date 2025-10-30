package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.MuteDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_mutes.
 * Columns inferred from MuteDTO:
 * id BIGINT AUTO_INCREMENT PRIMARY KEY,
 * created_at TEXT NOT NULL,
 * expires_at TEXT NULL,
 * reason TEXT NOT NULL,
 * player_uuid TEXT NOT NULL,
 * muted_by_uuid TEXT NOT NULL
 * // TODO: switch TEXT to TIMESTAMP if schema uses real timestamps.
 * // TODO: add indexes on player_uuid and expires_at.
 */
public final class MutesCrudManager {
    private final DatabaseManager db;

    public MutesCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static MuteDTO mapMute(ResultSet rs) throws SQLException {
        final long id = rs.getLong("m_id");
        final String createdAt = rs.getString("m_created_at");
        final String expiresAt = rs.getString("m_expires_at"); // may be null
        final String reason = rs.getString("m_reason");
        final String playerUuid = rs.getString("m_player_uuid");
        final String mutedByUuid = rs.getString("m_muted_by_uuid");
        return new MuteDTO(id, createdAt, expiresAt, reason, playerUuid, mutedByUuid);
    }

    // -- Read

    /**
     * Insert a new mute. Returns the persisted row.
     */
    public MuteDTO createMute(@NotNull String createdAt,
                              String expiresAt,
                              @NotNull String reason,
                              @NotNull String playerUuid,
                              @NotNull String mutedByUuid) {
        final String sql = """
                INSERT INTO sb_mutes (created_at, expires_at, reason, player_uuid, muted_by_uuid)
                VALUES (?, ?, ?, ?, ?)
                """;
        long id;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, createdAt);
            if (expiresAt == null) ps.setNull(2, Types.VARCHAR);
            else ps.setString(2, expiresAt);
            ps.setString(3, reason);
            ps.setString(4, playerUuid);
            ps.setString(5, mutedByUuid);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("createMute: no generated key");
                id = keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createMute failed for player=" + playerUuid, e);
        }
        return getMuteById(id).orElseThrow(() -> new RuntimeException("createMute post-fetch missing id=" + id));
    }

    /**
     * Get by id.
     */
    public Optional<MuteDTO> getMuteById(long id) {
        final String sql = """
                SELECT
                    m.id             AS m_id,
                    m.created_at     AS m_created_at,
                    m.expires_at     AS m_expires_at,
                    m.reason         AS m_reason,
                    m.player_uuid    AS m_player_uuid,
                    m.muted_by_uuid  AS m_muted_by_uuid
                FROM sb_mutes m
                WHERE m.id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapMute(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getMuteById failed id=" + id, e);
        }
    }

    /**
     * List all mutes.
     */
    public List<MuteDTO> getAllMutes() {
        final String sql = """
                SELECT
                    m.id             AS m_id,
                    m.created_at     AS m_created_at,
                    m.expires_at     AS m_expires_at,
                    m.reason         AS m_reason,
                    m.player_uuid    AS m_player_uuid,
                    m.muted_by_uuid  AS m_muted_by_uuid
                FROM sb_mutes m
                ORDER BY m.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<MuteDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapMute(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllMutes failed", e);
        }
    }

    /**
     * List mutes by player.
     */
    public List<MuteDTO> getMutesByPlayerUuid(@NotNull String playerUuid) {
        final String sql = """
                SELECT
                    m.id             AS m_id,
                    m.created_at     AS m_created_at,
                    m.expires_at     AS m_expires_at,
                    m.reason         AS m_reason,
                    m.player_uuid    AS m_player_uuid,
                    m.muted_by_uuid  AS m_muted_by_uuid
                FROM sb_mutes m
                WHERE m.player_uuid = ?
                ORDER BY m.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<MuteDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapMute(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getMutesByPlayerUuid failed for player=" + playerUuid, e);
        }
    }

    // -- Update

    /**
     * List currently effective mutes for a player, comparing ISO-8601 strings.
     * Pass nowIso as the current timestamp string.
     * TODO: If the schema uses TIMESTAMP, compare using DB time functions instead.
     */
    public List<MuteDTO> getActiveMutesByPlayerUuid(@NotNull String playerUuid, @NotNull String nowIso) {
        final String sql = """
                SELECT
                    m.id             AS m_id,
                    m.created_at     AS m_created_at,
                    m.expires_at     AS m_expires_at,
                    m.reason         AS m_reason,
                    m.player_uuid    AS m_player_uuid,
                    m.muted_by_uuid  AS m_muted_by_uuid
                FROM sb_mutes m
                WHERE m.player_uuid = ?
                  AND (m.expires_at IS NULL OR m.expires_at > ?)
                ORDER BY m.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setString(2, nowIso);
            try (ResultSet rs = ps.executeQuery()) {
                final List<MuteDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapMute(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getActiveMutesByPlayerUuid failed for player=" + playerUuid, e);
        }
    }

    /**
     * Update reason and expiresAt.
     */
    public MuteDTO updateMute(long id, @NotNull String reason, String expiresAt) {
        final String sql = """
                UPDATE sb_mutes
                SET reason = ?, expires_at = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reason);
            if (expiresAt == null) ps.setNull(2, Types.VARCHAR);
            else ps.setString(2, expiresAt);
            ps.setLong(3, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("updateMute affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("updateMute failed id=" + id, e);
        }
        return getMuteById(id).orElseThrow(() -> new RuntimeException("updateMute post-fetch missing id=" + id));
    }

    // -- Delete

    /**
     * Convenience: update only expiry.
     */
    public MuteDTO setMuteExpiry(long id, String expiresAt) {
        final String sql = """
                UPDATE sb_mutes
                SET expires_at = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (expiresAt == null) ps.setNull(1, Types.VARCHAR);
            else ps.setString(1, expiresAt);
            ps.setLong(2, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setMuteExpiry affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("setMuteExpiry failed id=" + id, e);
        }
        return getMuteById(id).orElseThrow(() -> new RuntimeException("setMuteExpiry post-fetch missing id=" + id));
    }

    // -- Mapper

    /**
     * Delete by id and return prior row if it existed.
     */
    public Optional<MuteDTO> deleteMuteById(long id) {
        final Optional<MuteDTO> before = getMuteById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_mutes
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteMuteById failed id=" + id, e);
        }
    }
}
