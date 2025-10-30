package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.ChatLogDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_chat_logs.
 * Columns per DTO:
 * id BIGINT AUTO_INCREMENT PRIMARY KEY,
 * at TEXT NOT NULL,                 // ISO-8601 recommended
 * content TEXT NOT NULL,
 * sender_player_uuid TEXT NOT NULL
 * TODO: add index on (sender_player_uuid, at).
 */
public final class ChatLogsCrudManager {
    private final DatabaseManager db;

    public ChatLogsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static ChatLogDTO mapChatLog(ResultSet rs) throws SQLException {
        final long id = rs.getLong("cl_id");
        final String at = rs.getString("cl_at");
        final String content = rs.getString("cl_content");
        final String sender = rs.getString("cl_sender_player_uuid");
        return new ChatLogDTO(id, at, content, sender);
    }

    // -- Read

    /**
     * Insert a new chat log entry. Returns the persisted row.
     */
    public ChatLogDTO createChatLog(@NotNull String at,
                                    @NotNull String content,
                                    @NotNull String senderPlayerUuid) {
        final String sql = """
                INSERT INTO sb_chat_logs (at, content, sender_player_uuid)
                VALUES (?, ?, ?)
                """;
        long id;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, at);
            ps.setString(2, content);
            ps.setString(3, senderPlayerUuid);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("createChatLog: no generated key");
                id = keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createChatLog failed for sender=" + senderPlayerUuid, e);
        }
        return getChatLogById(id).orElseThrow(() -> new RuntimeException("createChatLog post-fetch missing id=" + id));
    }

    /**
     * Get one by id.
     */
    public Optional<ChatLogDTO> getChatLogById(long id) {
        final String sql = """
                SELECT
                    cl.id                  AS cl_id,
                    cl.at                  AS cl_at,
                    cl.content             AS cl_content,
                    cl.sender_player_uuid  AS cl_sender_player_uuid
                FROM sb_chat_logs cl
                WHERE cl.id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapChatLog(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getChatLogById failed id=" + id, e);
        }
    }

    /**
     * List all chat logs.
     */
    public List<ChatLogDTO> getAllChatLogs() {
        final String sql = """
                SELECT
                    cl.id                  AS cl_id,
                    cl.at                  AS cl_at,
                    cl.content             AS cl_content,
                    cl.sender_player_uuid  AS cl_sender_player_uuid
                FROM sb_chat_logs cl
                ORDER BY cl.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<ChatLogDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapChatLog(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllChatLogs failed", e);
        }
    }

    /**
     * List chat logs by sender.
     */
    public List<ChatLogDTO> getChatLogsBySenderUuid(@NotNull String senderPlayerUuid) {
        final String sql = """
                SELECT
                    cl.id                  AS cl_id,
                    cl.at                  AS cl_at,
                    cl.content             AS cl_content,
                    cl.sender_player_uuid  AS cl_sender_player_uuid
                FROM sb_chat_logs cl
                WHERE cl.sender_player_uuid = ?
                ORDER BY cl.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, senderPlayerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<ChatLogDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapChatLog(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getChatLogsBySenderUuid failed for sender=" + senderPlayerUuid, e);
        }
    }

    // -- Update

    /**
     * List chat logs for a sender between [fromIso, toIso].
     */
    public List<ChatLogDTO> getChatLogsBySenderBetween(@NotNull String senderPlayerUuid,
                                                       @NotNull String fromIso,
                                                       @NotNull String toIso) {
        final String sql = """
                SELECT
                    cl.id                  AS cl_id,
                    cl.at                  AS cl_at,
                    cl.content             AS cl_content,
                    cl.sender_player_uuid  AS cl_sender_player_uuid
                FROM sb_chat_logs cl
                WHERE cl.sender_player_uuid = ?
                  AND cl.at >= ?
                  AND cl.at <= ?
                ORDER BY cl.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, senderPlayerUuid);
            ps.setString(2, fromIso);
            ps.setString(3, toIso);
            try (ResultSet rs = ps.executeQuery()) {
                final List<ChatLogDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapChatLog(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getChatLogsBySenderBetween failed for sender=" + senderPlayerUuid, e);
        }
    }

    /**
     * Update all fields except id.
     */
    public ChatLogDTO updateChatLog(long id,
                                    @NotNull String at,
                                    @NotNull String content,
                                    @NotNull String senderPlayerUuid) {
        final String sql = """
                UPDATE sb_chat_logs
                SET at = ?, content = ?, sender_player_uuid = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, at);
            ps.setString(2, content);
            ps.setString(3, senderPlayerUuid);
            ps.setLong(4, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("updateChatLog affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("updateChatLog failed id=" + id, e);
        }
        return getChatLogById(id).orElseThrow(() -> new RuntimeException("updateChatLog post-fetch missing id=" + id));
    }

    // -- Delete

    /**
     * Update only content.
     */
    public ChatLogDTO setChatLogContent(long id, @NotNull String content) {
        final String sql = """
                UPDATE sb_chat_logs
                SET content = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, content);
            ps.setLong(2, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setChatLogContent affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("setChatLogContent failed id=" + id, e);
        }
        return getChatLogById(id).orElseThrow(() -> new RuntimeException("setChatLogContent post-fetch missing id=" + id));
    }

    // -- Mapper

    /**
     * Delete by id and return prior row if it existed.
     */
    public Optional<ChatLogDTO> deleteChatLogById(long id) {
        final Optional<ChatLogDTO> before = getChatLogById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_chat_logs
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteChatLogById failed id=" + id, e);
        }
    }
}
