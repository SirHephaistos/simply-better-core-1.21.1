package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.BanIpDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_bans_ip.
 * Columns per DTO:
 * id BIGINT AUTO_INCREMENT PRIMARY KEY,
 * created_at TEXT NOT NULL,
 * expires_at TEXT NULL,
 * reason TEXT NOT NULL,
 * ip_address TEXT NOT NULL,
 * player_uuid TEXT NULL,
 * banned_by_uuid TEXT NOT NULL
 * // TODO: switch TEXT to TIMESTAMP if schema uses real timestamps.
 * // TODO: add indexes on ip_address, player_uuid, expires_at.
 */
public final class BansIpCrudManager {
    private final DatabaseManager db;

    public BansIpCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static BanIpDTO mapBanIp(ResultSet rs) throws SQLException {
        final long id = rs.getLong("b_id");
        final String createdAt = rs.getString("b_created_at");
        final String expiresAt = rs.getString("b_expires_at"); // may be null
        final String reason = rs.getString("b_reason");
        final String ipAddress = rs.getString("b_ip_address");
        final String playerUuid = rs.getString("b_player_uuid"); // may be null
        final String bannedByUuid = rs.getString("b_banned_by_uuid");
        return new BanIpDTO(id, createdAt, expiresAt, reason, ipAddress, playerUuid, bannedByUuid);
        // Note: DTO uses boxed Long for id; JDBC long maps fine.
    }

    // -- Read

    /**
     * Insert a new IP ban. Returns the persisted row.
     */
    public BanIpDTO createBanIp(@NotNull String createdAt,
                                String expiresAt,
                                @NotNull String reason,
                                @NotNull String ipAddress,
                                String playerUuid,
                                @NotNull String bannedByUuid) {
        final String sql = """
                INSERT INTO sb_bans_ip (created_at, expires_at, reason, ip_address, player_uuid, banned_by_uuid)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        long id;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, createdAt);
            if (expiresAt == null) ps.setNull(2, Types.VARCHAR);
            else ps.setString(2, expiresAt);
            ps.setString(3, reason);
            ps.setString(4, ipAddress);
            if (playerUuid == null) ps.setNull(5, Types.VARCHAR);
            else ps.setString(5, playerUuid);
            ps.setString(6, bannedByUuid);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("createBanIp: no generated key");
                id = keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createBanIp failed for ip=" + ipAddress, e);
        }
        return getBanIpById(id).orElseThrow(() -> new RuntimeException("createBanIp post-fetch missing id=" + id));
    }

    /**
     * Get by id.
     */
    public Optional<BanIpDTO> getBanIpById(long id) {
        final String sql = """
                SELECT
                    b.id             AS b_id,
                    b.created_at     AS b_created_at,
                    b.expires_at     AS b_expires_at,
                    b.reason         AS b_reason,
                    b.ip_address     AS b_ip_address,
                    b.player_uuid    AS b_player_uuid,
                    b.banned_by_uuid AS b_banned_by_uuid
                FROM sb_bans_ip b
                WHERE b.id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapBanIp(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getBanIpById failed id=" + id, e);
        }
    }

    /**
     * List all IP bans.
     */
    public List<BanIpDTO> getAllBanIps() {
        final String sql = """
                SELECT
                    b.id             AS b_id,
                    b.created_at     AS b_created_at,
                    b.expires_at     AS b_expires_at,
                    b.reason         AS b_reason,
                    b.ip_address     AS b_ip_address,
                    b.player_uuid    AS b_player_uuid,
                    b.banned_by_uuid AS b_banned_by_uuid
                FROM sb_bans_ip b
                ORDER BY b.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<BanIpDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapBanIp(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllBanIps failed", e);
        }
    }

    /**
     * List IP bans by exact IP address.
     */
    public List<BanIpDTO> getBanIpsByIpAddress(@NotNull String ipAddress) {
        final String sql = """
                SELECT
                    b.id             AS b_id,
                    b.created_at     AS b_created_at,
                    b.expires_at     AS b_expires_at,
                    b.reason         AS b_reason,
                    b.ip_address     AS b_ip_address,
                    b.player_uuid    AS b_player_uuid,
                    b.banned_by_uuid AS b_banned_by_uuid
                FROM sb_bans_ip b
                WHERE b.ip_address = ?
                ORDER BY b.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ipAddress);
            try (ResultSet rs = ps.executeQuery()) {
                final List<BanIpDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapBanIp(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getBanIpsByIpAddress failed for ip=" + ipAddress, e);
        }
    }

    /**
     * List currently effective IP bans for an IP, comparing ISO-8601 strings.
     * Pass nowIso as the current timestamp string.
     * TODO: If the schema uses TIMESTAMP, compare using DB time functions instead.
     */
    public List<BanIpDTO> getActiveBanIpsByIpAddress(@NotNull String ipAddress, @NotNull String nowIso) {
        final String sql = """
                SELECT
                    b.id             AS b_id,
                    b.created_at     AS b_created_at,
                    b.expires_at     AS b_expires_at,
                    b.reason         AS b_reason,
                    b.ip_address     AS b_ip_address,
                    b.player_uuid    AS b_player_uuid,
                    b.banned_by_uuid AS b_banned_by_uuid
                FROM sb_bans_ip b
                WHERE b.ip_address = ?
                  AND (b.expires_at IS NULL OR b.expires_at > ?)
                ORDER BY b.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ipAddress);
            ps.setString(2, nowIso);
            try (ResultSet rs = ps.executeQuery()) {
                final List<BanIpDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapBanIp(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getActiveBanIpsByIpAddress failed for ip=" + ipAddress, e);
        }
    }

    // -- Update

    /**
     * List IP bans created for a specific player UUID, if recorded.
     */
    public List<BanIpDTO> getBanIpsByPlayerUuid(@NotNull String playerUuid) {
        final String sql = """
                SELECT
                    b.id             AS b_id,
                    b.created_at     AS b_created_at,
                    b.expires_at     AS b_expires_at,
                    b.reason         AS b_reason,
                    b.ip_address     AS b_ip_address,
                    b.player_uuid    AS b_player_uuid,
                    b.banned_by_uuid AS b_banned_by_uuid
                FROM sb_bans_ip b
                WHERE b.player_uuid = ?
                ORDER BY b.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<BanIpDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapBanIp(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getBanIpsByPlayerUuid failed for player=" + playerUuid, e);
        }
    }

    /**
     * Update reason and expiresAt.
     */
    public BanIpDTO updateBanIp(long id, @NotNull String reason, String expiresAt) {
        final String sql = """
                UPDATE sb_bans_ip
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
            if (upd == 0) throw new RuntimeException("updateBanIp affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("updateBanIp failed id=" + id, e);
        }
        return getBanIpById(id).orElseThrow(() -> new RuntimeException("updateBanIp post-fetch missing id=" + id));
    }

    // -- Delete

    /**
     * Convenience: update only expiry.
     */
    public BanIpDTO setBanIpExpiry(long id, String expiresAt) {
        final String sql = """
                UPDATE sb_bans_ip
                SET expires_at = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (expiresAt == null) ps.setNull(1, Types.VARCHAR);
            else ps.setString(1, expiresAt);
            ps.setLong(2, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setBanIpExpiry affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("setBanIpExpiry failed id=" + id, e);
        }
        return getBanIpById(id).orElseThrow(() -> new RuntimeException("setBanIpExpiry post-fetch missing id=" + id));
    }

    // -- Mapper

    /**
     * Delete by id and return prior row if it existed.
     */
    public Optional<BanIpDTO> deleteBanIpById(long id) {
        final Optional<BanIpDTO> before = getBanIpById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_bans_ip
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteBanIpById failed id=" + id, e);
        }
    }
}
