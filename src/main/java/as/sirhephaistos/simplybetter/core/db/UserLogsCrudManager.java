package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.UserLogDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_user_logs.
 * Columns per DTO:
 * id BIGINT AUTO_INCREMENT PRIMARY KEY,
 * at TEXT NOT NULL,                       // ISO-8601 recommended
 * description TEXT NOT NULL,
 * player_uuid TEXT NOT NULL,
 * player_position_id BIGINT NULL,
 * interact_position_id BIGINT NULL
 * TODO: add FK constraints to sb_positions(id). Add indexes on (player_uuid, at) and position ids.
 */
public final class UserLogsCrudManager {
    private final DatabaseManager db;

    public UserLogsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static UserLogDTO mapUserLog(ResultSet rs) throws SQLException {
        final long id = rs.getLong("ul_id");
        final String at = rs.getString("ul_at");
        final String description = rs.getString("ul_description");
        final String playerUuid = rs.getString("ul_player_uuid");
        final long pPos = rs.getLong("ul_player_position_id");
        final Long playerPositionId = rs.wasNull() ? null : pPos;
        final long iPos = rs.getLong("ul_interact_position_id");
        final Long interactPositionId = rs.wasNull() ? null : iPos;
        return new UserLogDTO(id, at, description, playerUuid, playerPositionId, interactPositionId);
    }

    // -- Read

    /**
     * Insert a new user log entry. Returns the persisted row.
     */
    public UserLogDTO createUserLog(@NotNull String at,
                                    @NotNull String description,
                                    @NotNull String playerUuid,
                                    Long playerPositionId,
                                    Long interactPositionId) {
        final String sql = """
                INSERT INTO sb_user_logs (at, description, player_uuid, player_position_id, interact_position_id)
                VALUES (?, ?, ?, ?, ?)
                """;
        long id;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, at);
            ps.setString(2, description);
            ps.setString(3, playerUuid);
            if (playerPositionId == null) ps.setNull(4, Types.BIGINT);
            else ps.setLong(4, playerPositionId);
            if (interactPositionId == null) ps.setNull(5, Types.BIGINT);
            else ps.setLong(5, interactPositionId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("createUserLog: no generated key");
                id = keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createUserLog failed for player=" + playerUuid, e);
        }
        return getUserLogById(id).orElseThrow(() -> new RuntimeException("createUserLog post-fetch missing id=" + id));
    }

    /**
     * Get one by id.
     */
    public Optional<UserLogDTO> getUserLogById(long id) {
        final String sql = """
                SELECT
                    ul.id                   AS ul_id,
                    ul.at                   AS ul_at,
                    ul.description          AS ul_description,
                    ul.player_uuid          AS ul_player_uuid,
                    ul.player_position_id   AS ul_player_position_id,
                    ul.interact_position_id AS ul_interact_position_id
                FROM sb_user_logs ul
                WHERE ul.id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapUserLog(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getUserLogById failed id=" + id, e);
        }
    }

    /**
     * List all logs.
     */
    public List<UserLogDTO> getAllUserLogs() {
        final String sql = """
                SELECT
                    ul.id                   AS ul_id,
                    ul.at                   AS ul_at,
                    ul.description          AS ul_description,
                    ul.player_uuid          AS ul_player_uuid,
                    ul.player_position_id   AS ul_player_position_id,
                    ul.interact_position_id AS ul_interact_position_id
                FROM sb_user_logs ul
                ORDER BY ul.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<UserLogDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapUserLog(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllUserLogs failed", e);
        }
    }

    /**
     * List all logs for a player.
     */
    public List<UserLogDTO> getUserLogsByPlayerUuid(@NotNull String playerUuid) {
        final String sql = """
                SELECT
                    ul.id                   AS ul_id,
                    ul.at                   AS ul_at,
                    ul.description          AS ul_description,
                    ul.player_uuid          AS ul_player_uuid,
                    ul.player_position_id   AS ul_player_position_id,
                    ul.interact_position_id AS ul_interact_position_id
                FROM sb_user_logs ul
                WHERE ul.player_uuid = ?
                ORDER BY ul.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<UserLogDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapUserLog(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getUserLogsByPlayerUuid failed for player=" + playerUuid, e);
        }
    }

    /**
     * List player logs between [fromIso, toIso].
     */
    public List<UserLogDTO> getUserLogsByPlayerBetween(@NotNull String playerUuid,
                                                       @NotNull String fromIso,
                                                       @NotNull String toIso) {
        final String sql = """
                SELECT
                    ul.id                   AS ul_id,
                    ul.at                   AS ul_at,
                    ul.description          AS ul_description,
                    ul.player_uuid          AS ul_player_uuid,
                    ul.player_position_id   AS ul_player_position_id,
                    ul.interact_position_id AS ul_interact_position_id
                FROM sb_user_logs ul
                WHERE ul.player_uuid = ?
                  AND ul.at >= ?
                  AND ul.at <= ?
                ORDER BY ul.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setString(2, fromIso);
            ps.setString(3, toIso);
            try (ResultSet rs = ps.executeQuery()) {
                final List<UserLogDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapUserLog(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getUserLogsByPlayerBetween failed for player=" + playerUuid, e);
        }
    }

    /**
     * List logs by player_position_id. Use null to fetch rows with NULL.
     */
    public List<UserLogDTO> getUserLogsByPlayerPositionId(Long playerPositionId) {
        final String sql = """
                SELECT
                    ul.id                   AS ul_id,
                    ul.at                   AS ul_at,
                    ul.description          AS ul_description,
                    ul.player_uuid          AS ul_player_uuid,
                    ul.player_position_id   AS ul_player_position_id,
                    ul.interact_position_id AS ul_interact_position_id
                FROM sb_user_logs ul
                WHERE (? IS NULL AND ul.player_position_id IS NULL)
                   OR (? IS NOT NULL AND ul.player_position_id = ?)
                ORDER BY ul.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (playerPositionId == null) {
                ps.setNull(1, Types.BIGINT);
                ps.setNull(2, Types.BIGINT);
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setNull(1, Types.BIGINT);
                ps.setLong(2, playerPositionId);
                ps.setLong(3, playerPositionId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                final List<UserLogDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapUserLog(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getUserLogsByPlayerPositionId failed", e);
        }
    }

    // -- Update

    /**
     * List logs by interact_position_id. Use null to fetch rows with NULL.
     */
    public List<UserLogDTO> getUserLogsByInteractPositionId(Long interactPositionId) {
        final String sql = """
                SELECT
                    ul.id                   AS ul_id,
                    ul.at                   AS ul_at,
                    ul.description          AS ul_description,
                    ul.player_uuid          AS ul_player_uuid,
                    ul.player_position_id   AS ul_player_position_id,
                    ul.interact_position_id AS ul_interact_position_id
                FROM sb_user_logs ul
                WHERE (? IS NULL AND ul.interact_position_id IS NULL)
                   OR (? IS NOT NULL AND ul.interact_position_id = ?)
                ORDER BY ul.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (interactPositionId == null) {
                ps.setNull(1, Types.BIGINT);
                ps.setNull(2, Types.BIGINT);
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setNull(1, Types.BIGINT);
                ps.setLong(2, interactPositionId);
                ps.setLong(3, interactPositionId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                final List<UserLogDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapUserLog(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getUserLogsByInteractPositionId failed", e);
        }
    }

    /**
     * Update all mutable fields except id.
     */
    public UserLogDTO updateUserLog(long id,
                                    @NotNull String at,
                                    @NotNull String description,
                                    @NotNull String playerUuid,
                                    Long playerPositionId,
                                    Long interactPositionId) {
        final String sql = """
                UPDATE sb_user_logs
                SET at = ?, description = ?, player_uuid = ?, player_position_id = ?, interact_position_id = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, at);
            ps.setString(2, description);
            ps.setString(3, playerUuid);
            if (playerPositionId == null) ps.setNull(4, Types.BIGINT);
            else ps.setLong(4, playerPositionId);
            if (interactPositionId == null) ps.setNull(5, Types.BIGINT);
            else ps.setLong(5, interactPositionId);
            ps.setLong(6, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("updateUserLog affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("updateUserLog failed id=" + id, e);
        }
        return getUserLogById(id).orElseThrow(() -> new RuntimeException("updateUserLog post-fetch missing id=" + id));
    }

    // -- Delete

    /**
     * Update only description.
     */
    public UserLogDTO setUserLogDescription(long id, @NotNull String description) {
        final String sql = """
                UPDATE sb_user_logs
                SET description = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, description);
            ps.setLong(2, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setUserLogDescription affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("setUserLogDescription failed id=" + id, e);
        }
        return getUserLogById(id).orElseThrow(() -> new RuntimeException("setUserLogDescription post-fetch missing id=" + id));
    }

    // -- Mapper

    /**
     * Delete by id and return prior row if it existed.
     */
    public Optional<UserLogDTO> deleteUserLogById(long id) {
        final Optional<UserLogDTO> before = getUserLogById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_user_logs
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteUserLogById failed id=" + id, e);
        }
    }
}
