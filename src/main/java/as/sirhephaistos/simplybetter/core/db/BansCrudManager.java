package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.BanDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_bans.
 * Columns inferred from BanDTO:
 * id BIGINT AUTO_INCREMENT PRIMARY KEY,
 * created_at TEXT NOT NULL,
 * expires_at TEXT NULL,
 * reason TEXT NOT NULL,
 * player_uuid TEXT NOT NULL,
 * banned_by_uuid TEXT NOT NULL
 * // TODO: switch TEXT to TIMESTAMP if schema uses real timestamps.
 * // TODO: add indexes on player_uuid and expires_at.
 */
public final class BansCrudManager {
    private final DatabaseManager db;

    public BansCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static BanDTO mapBan(ResultSet rs) throws SQLException {
        final long id = rs.getLong("b_id");
        final String createdAt = rs.getString("b_created_at");
        final String expiresAt = rs.getString("b_expires_at"); // may be null
        final String reason = rs.getString("b_reason");
        final String playerUuid = rs.getString("b_player_uuid");
        final String bannedByUuid = rs.getString("b_banned_by_uuid");
        return new BanDTO(id, createdAt, expiresAt, reason, playerUuid, bannedByUuid);
    }

    // -- Read

    /**
     * Insert a new ban. Returns the persisted row.
     */
    public BanDTO createBan(@NotNull String createdAt,
                            String expiresAt,
                            @NotNull String reason,
                            @NotNull String playerUuid,
                            @NotNull String bannedByUuid) {
        final String sql = """
                INSERT INTO sb_bans (created_at, expires_at, reason, player_uuid, banned_by_uuid)
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
            ps.setString(5, bannedByUuid);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("createBan: no generated key");
                id = keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createBan failed for player=" + playerUuid, e);
        }
        return getBanById(id).orElseThrow(() -> new RuntimeException("createBan post-fetch missing id=" + id));
    }

    /**
     * Get by id.
     */
    public Optional<BanDTO> getBanById(long id) {
        final String sql = """
                SELECT
                    b.id            AS b_id,
                    b.created_at    AS b_created_at,
                    b.expires_at    AS b_expires_at,
                    b.reason        AS b_reason,
                    b.player_uuid   AS b_player_uuid,
                    b.banned_by_uuid AS b_banned_by_uuid
                FROM sb_bans b
                WHERE b.id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapBan(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getBanById failed id=" + id, e);
        }
    }

    /**
     * List all bans.
     */
    public List<BanDTO> getAllBans() {
        final String sql = """
                SELECT
                    b.id            AS b_id,
                    b.created_at    AS b_created_at,
                    b.expires_at    AS b_expires_at,
                    b.reason        AS b_reason,
                    b.player_uuid   AS b_player_uuid,
                    b.banned_by_uuid AS b_banned_by_uuid
                FROM sb_bans b
                ORDER BY b.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<BanDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapBan(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllBans failed", e);
        }
    }

    /**
     * List bans by player.
     */
    public List<BanDTO> getBansByPlayerUuid(@NotNull String playerUuid) {
        final String sql = """
                SELECT
                    b.id            AS b_id,
                    b.created_at    AS b_created_at,
                    b.expires_at    AS b_expires_at,
                    b.reason        AS b_reason,
                    b.player_uuid   AS b_player_uuid,
                    b.banned_by_uuid AS b_banned_by_uuid
                FROM sb_bans b
                WHERE b.player_uuid = ?
                ORDER BY b.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<BanDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapBan(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getBansByPlayerUuid failed for player=" + playerUuid, e);
        }
    }

    // -- Update

    /**
     * List currently effective bans for a player, comparing ISO-8601 strings.
     * Pass nowIso as the current timestamp string.
     * TODO: If the schema uses TIMESTAMP, compare using DB time functions instead.
     */
    public List<BanDTO> getActiveBansByPlayerUuid(@NotNull String playerUuid, @NotNull String nowIso) {
        final String sql = """
                SELECT
                    b.id            AS b_id,
                    b.created_at    AS b_created_at,
                    b.expires_at    AS b_expires_at,
                    b.reason        AS b_reason,
                    b.player_uuid   AS b_player_uuid,
                    b.banned_by_uuid AS b_banned_by_uuid
                FROM sb_bans b
                WHERE b.player_uuid = ?
                  AND (b.expires_at IS NULL OR b.expires_at > ?)
                ORDER BY b.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setString(2, nowIso);
            try (ResultSet rs = ps.executeQuery()) {
                final List<BanDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapBan(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getActiveBansByPlayerUuid failed for player=" + playerUuid, e);
        }
    }

    /**
     * Update reason and expiresAt.
     */
    public BanDTO updateBan(long id, @NotNull String reason, String expiresAt) {
        final String sql = """
                UPDATE sb_bans
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
            if (upd == 0) throw new RuntimeException("updateBan affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("updateBan failed id=" + id, e);
        }
        return getBanById(id).orElseThrow(() -> new RuntimeException("updateBan post-fetch missing id=" + id));
    }

    // -- Delete

    /**
     * Convenience: shorten or clear expiry.
     */
    public BanDTO setBanExpiry(long id, String expiresAt) {
        final String sql = """
                UPDATE sb_bans
                SET expires_at = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (expiresAt == null) ps.setNull(1, Types.VARCHAR);
            else ps.setString(1, expiresAt);
            ps.setLong(2, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setBanExpiry affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("setBanExpiry failed id=" + id, e);
        }
        return getBanById(id).orElseThrow(() -> new RuntimeException("setBanExpiry post-fetch missing id=" + id));
    }

    // -- Mapper

    /**
     * Delete by id and return prior row if it existed.
     */
    public Optional<BanDTO> deleteBanById(long id) {
        final Optional<BanDTO> before = getBanById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_bans
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteBanById failed id=" + id, e);
        }
    }
}
