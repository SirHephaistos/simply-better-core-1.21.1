package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.PrivateChatLogDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_private_chat_logs.
 * Columns per DTO:
 * id BIGINT AUTO_INCREMENT PRIMARY KEY,
 * at TEXT NOT NULL,
 * content TEXT NOT NULL,
 * sender_player_uuid TEXT NOT NULL,
 * receiver_player_uuid TEXT NOT NULL
 * // TODO: add index on (sender_player_uuid, receiver_player_uuid, at).
 */
public final class PrivateChatLogsCrudManager {
    private final DatabaseManager db;

    public PrivateChatLogsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static PrivateChatLogDTO mapPrivateChatLog(ResultSet rs) throws SQLException {
        final long id = rs.getLong("pcl_id");
        final String at = rs.getString("pcl_at");
        final String content = rs.getString("pcl_content");
        final String sender = rs.getString("pcl_sender_player_uuid");
        final String receiver = rs.getString("pcl_receiver_player_uuid");
        return new PrivateChatLogDTO(id, at, content, sender, receiver);
    }

    // -- Read

    /**
     * Insert a new private chat log entry.
     */
    public PrivateChatLogDTO createPrivateChatLog(@NotNull String at,
                                                  @NotNull String content,
                                                  @NotNull String senderPlayerUuid,
                                                  @NotNull String receiverPlayerUuid) {
        final String sql = """
                INSERT INTO sb_private_chat_logs (at, content, sender_player_uuid, receiver_player_uuid)
                VALUES (?, ?, ?, ?)
                """;
        long id;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, at);
            ps.setString(2, content);
            ps.setString(3, senderPlayerUuid);
            ps.setString(4, receiverPlayerUuid);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("createPrivateChatLog: no generated key");
                id = keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createPrivateChatLog failed sender=" + senderPlayerUuid + " -> receiver=" + receiverPlayerUuid, e);
        }
        return getPrivateChatLogById(id)
                .orElseThrow(() -> new RuntimeException("createPrivateChatLog post-fetch missing id=" + id));
    }

    /**
     * Get by id.
     */
    public Optional<PrivateChatLogDTO> getPrivateChatLogById(long id) {
        final String sql = """
                SELECT
                    pcl.id                    AS pcl_id,
                    pcl.at                    AS pcl_at,
                    pcl.content               AS pcl_content,
                    pcl.sender_player_uuid    AS pcl_sender_player_uuid,
                    pcl.receiver_player_uuid  AS pcl_receiver_player_uuid
                FROM sb_private_chat_logs pcl
                WHERE pcl.id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapPrivateChatLog(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getPrivateChatLogById failed id=" + id, e);
        }
    }

    /**
     * List all rows.
     */
    public List<PrivateChatLogDTO> getAllPrivateChatLogs() {
        final String sql = """
                SELECT
                    pcl.id                    AS pcl_id,
                    pcl.at                    AS pcl_at,
                    pcl.content               AS pcl_content,
                    pcl.sender_player_uuid    AS pcl_sender_player_uuid,
                    pcl.receiver_player_uuid  AS pcl_receiver_player_uuid
                FROM sb_private_chat_logs pcl
                ORDER BY pcl.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<PrivateChatLogDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapPrivateChatLog(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllPrivateChatLogs failed", e);
        }
    }

    /**
     * List by sender.
     */
    public List<PrivateChatLogDTO> getPrivateChatLogsBySender(@NotNull String senderPlayerUuid) {
        final String sql = """
                SELECT
                    pcl.id                    AS pcl_id,
                    pcl.at                    AS pcl_at,
                    pcl.content               AS pcl_content,
                    pcl.sender_player_uuid    AS pcl_sender_player_uuid,
                    pcl.receiver_player_uuid  AS pcl_receiver_player_uuid
                FROM sb_private_chat_logs pcl
                WHERE pcl.sender_player_uuid = ?
                ORDER BY pcl.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, senderPlayerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<PrivateChatLogDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapPrivateChatLog(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getPrivateChatLogsBySender failed sender=" + senderPlayerUuid, e);
        }
    }

    /**
     * List by receiver.
     */
    public List<PrivateChatLogDTO> getPrivateChatLogsByReceiver(@NotNull String receiverPlayerUuid) {
        final String sql = """
                SELECT
                    pcl.id                    AS pcl_id,
                    pcl.at                    AS pcl_at,
                    pcl.content               AS pcl_content,
                    pcl.sender_player_uuid    AS pcl_sender_player_uuid,
                    pcl.receiver_player_uuid  AS pcl_receiver_player_uuid
                FROM sb_private_chat_logs pcl
                WHERE pcl.receiver_player_uuid = ?
                ORDER BY pcl.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, receiverPlayerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<PrivateChatLogDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapPrivateChatLog(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getPrivateChatLogsByReceiver failed receiver=" + receiverPlayerUuid, e);
        }
    }

    /**
     * Conversation between two players, any direction.
     */
    public List<PrivateChatLogDTO> getConversationBetween(@NotNull String playerAUuid,
                                                          @NotNull String playerBUuid) {
        final String sql = """
                SELECT
                    pcl.id                    AS pcl_id,
                    pcl.at                    AS pcl_at,
                    pcl.content               AS pcl_content,
                    pcl.sender_player_uuid    AS pcl_sender_player_uuid,
                    pcl.receiver_player_uuid  AS pcl_receiver_player_uuid
                FROM sb_private_chat_logs pcl
                WHERE (pcl.sender_player_uuid = ? AND pcl.receiver_player_uuid = ?)
                   OR (pcl.sender_player_uuid = ? AND pcl.receiver_player_uuid = ?)
                ORDER BY pcl.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerAUuid);
            ps.setString(2, playerBUuid);
            ps.setString(3, playerBUuid);
            ps.setString(4, playerAUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<PrivateChatLogDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapPrivateChatLog(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getConversationBetween failed A=" + playerAUuid + " B=" + playerBUuid, e);
        }
    }

    // -- Update

    /**
     * Conversation between players in a time range [fromIso, toIso].
     */
    public List<PrivateChatLogDTO> getConversationBetweenDuring(@NotNull String playerAUuid,
                                                                @NotNull String playerBUuid,
                                                                @NotNull String fromIso,
                                                                @NotNull String toIso) {
        final String sql = """
                SELECT
                    pcl.id                    AS pcl_id,
                    pcl.at                    AS pcl_at,
                    pcl.content               AS pcl_content,
                    pcl.sender_player_uuid    AS pcl_sender_player_uuid,
                    pcl.receiver_player_uuid  AS pcl_receiver_player_uuid
                FROM sb_private_chat_logs pcl
                WHERE ((pcl.sender_player_uuid = ? AND pcl.receiver_player_uuid = ?)
                    OR (pcl.sender_player_uuid = ? AND pcl.receiver_player_uuid = ?))
                  AND pcl.at >= ?
                  AND pcl.at <= ?
                ORDER BY pcl.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerAUuid);
            ps.setString(2, playerBUuid);
            ps.setString(3, playerBUuid);
            ps.setString(4, playerAUuid);
            ps.setString(5, fromIso);
            ps.setString(6, toIso);
            try (ResultSet rs = ps.executeQuery()) {
                final List<PrivateChatLogDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapPrivateChatLog(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getConversationBetweenDuring failed A=" + playerAUuid + " B=" + playerBUuid, e);
        }
    }

    /**
     * Update all fields except id.
     */
    public PrivateChatLogDTO updatePrivateChatLog(long id,
                                                  @NotNull String at,
                                                  @NotNull String content,
                                                  @NotNull String senderPlayerUuid,
                                                  @NotNull String receiverPlayerUuid) {
        final String sql = """
                UPDATE sb_private_chat_logs
                SET at = ?, content = ?, sender_player_uuid = ?, receiver_player_uuid = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, at);
            ps.setString(2, content);
            ps.setString(3, senderPlayerUuid);
            ps.setString(4, receiverPlayerUuid);
            ps.setLong(5, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("updatePrivateChatLog affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("updatePrivateChatLog failed id=" + id, e);
        }
        return getPrivateChatLogById(id)
                .orElseThrow(() -> new RuntimeException("updatePrivateChatLog post-fetch missing id=" + id));
    }

    // -- Delete

    /**
     * Update content only.
     */
    public PrivateChatLogDTO setPrivateChatContent(long id, @NotNull String content) {
        final String sql = """
                UPDATE sb_private_chat_logs
                SET content = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, content);
            ps.setLong(2, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setPrivateChatContent affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("setPrivateChatContent failed id=" + id, e);
        }
        return getPrivateChatLogById(id)
                .orElseThrow(() -> new RuntimeException("setPrivateChatContent post-fetch missing id=" + id));
    }

    // -- Mapper

    /**
     * Delete by id and return prior row if it existed.
     */
    public Optional<PrivateChatLogDTO> deletePrivateChatLogById(long id) {
        final Optional<PrivateChatLogDTO> before = getPrivateChatLogById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_private_chat_logs
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deletePrivateChatLogById failed id=" + id, e);
        }
    }
}
