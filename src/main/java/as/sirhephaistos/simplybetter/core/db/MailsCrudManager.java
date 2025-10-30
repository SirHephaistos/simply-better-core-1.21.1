package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.MailDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_mails.
 * Schema (from project):
 * id INTEGER PK AUTOINCREMENT,
 * is_read INTEGER NOT NULL DEFAULT 0,
 * subject TEXT NOT NULL,
 * content TEXT NOT NULL,
 * read_at TEXT,
 * sent_at TEXT NOT NULL DEFAULT (datetime('now')),
 * expires_at TEXT,
 * sender_player_uuid TEXT NOT NULL,
 * target_player_uuid TEXT NOT NULL
 * Indexes: target+is_read, sender
 * TODO: switch TEXT times to TIMESTAMP if the project adopts typed timestamps.
 */
public final class MailsCrudManager {
    private final DatabaseManager db;

    public MailsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static MailDTO mapMail(ResultSet rs) throws SQLException {
        final long id = rs.getLong("m_id");
        final boolean isRead = rs.getInt("m_is_read") != 0;
        final String subject = rs.getString("m_subject");
        final String content = rs.getString("m_content");
        final String readAt = rs.getString("m_read_at");
        final String sentAt = rs.getString("m_sent_at");
        final String expiresAt = rs.getString("m_expires_at");
        final String sender = rs.getString("m_sender_player_uuid");
        final String target = rs.getString("m_target_player_uuid");
        return new MailDTO(id, isRead, subject, content, readAt, sentAt, expiresAt, sender, target);
    }

    // -- Read

    /**
     * Create a mail. If sentAt is null the DB default will be used.
     */
    public MailDTO createMail(@NotNull String subject,
                              @NotNull String content,
                              String sentAt,
                              String expiresAt,
                              @NotNull String senderPlayerUuid,
                              @NotNull String targetPlayerUuid) {
        final String sql = """
                INSERT INTO sb_mails (is_read, subject, content, read_at, sent_at, expires_at, sender_player_uuid, target_player_uuid)
                VALUES (0, ?, ?, NULL, ?, ?, ?, ?)
                """;
        long id;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, subject);
            ps.setString(2, content);
            if (sentAt == null) ps.setNull(3, Types.VARCHAR);
            else ps.setString(3, sentAt);
            if (expiresAt == null) ps.setNull(4, Types.VARCHAR);
            else ps.setString(4, expiresAt);
            ps.setString(5, senderPlayerUuid);
            ps.setString(6, targetPlayerUuid);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("createMail: no generated key");
                id = keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createMail failed sender=" + senderPlayerUuid + " -> target=" + targetPlayerUuid, e);
        }
        return getMailById(id).orElseThrow(() -> new RuntimeException("createMail post-fetch missing id=" + id));
    }

    /**
     * Get one by id.
     */
    public Optional<MailDTO> getMailById(long id) {
        final String sql = """
                SELECT
                    m.id                  AS m_id,
                    m.is_read             AS m_is_read,
                    m.subject             AS m_subject,
                    m.content             AS m_content,
                    m.read_at             AS m_read_at,
                    m.sent_at             AS m_sent_at,
                    m.expires_at          AS m_expires_at,
                    m.sender_player_uuid  AS m_sender_player_uuid,
                    m.target_player_uuid  AS m_target_player_uuid
                FROM sb_mails m
                WHERE m.id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapMail(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getMailById failed id=" + id, e);
        }
    }

    /**
     * List all mails.
     */
    public List<MailDTO> getAllMails() {
        final String sql = """
                SELECT
                    m.id                  AS m_id,
                    m.is_read             AS m_is_read,
                    m.subject             AS m_subject,
                    m.content             AS m_content,
                    m.read_at             AS m_read_at,
                    m.sent_at             AS m_sent_at,
                    m.expires_at          AS m_expires_at,
                    m.sender_player_uuid  AS m_sender_player_uuid,
                    m.target_player_uuid  AS m_target_player_uuid
                FROM sb_mails m
                ORDER BY m.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<MailDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapMail(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllMails failed", e);
        }
    }

    /**
     * Inbox for target. Most recent first.
     */
    public List<MailDTO> getInbox(@NotNull String targetPlayerUuid) {
        final String sql = """
                SELECT
                    m.id                  AS m_id,
                    m.is_read             AS m_is_read,
                    m.subject             AS m_subject,
                    m.content             AS m_content,
                    m.read_at             AS m_read_at,
                    m.sent_at             AS m_sent_at,
                    m.expires_at          AS m_expires_at,
                    m.sender_player_uuid  AS m_sender_player_uuid,
                    m.target_player_uuid  AS m_target_player_uuid
                FROM sb_mails m
                WHERE m.target_player_uuid = ?
                ORDER BY m.sent_at DESC, m.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, targetPlayerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<MailDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapMail(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getInbox failed for target=" + targetPlayerUuid, e);
        }
    }

    /**
     * Outbox for sender. Most recent first.
     */
    public List<MailDTO> getOutbox(@NotNull String senderPlayerUuid) {
        final String sql = """
                SELECT
                    m.id                  AS m_id,
                    m.is_read             AS m_is_read,
                    m.subject             AS m_subject,
                    m.content             AS m_content,
                    m.read_at             AS m_read_at,
                    m.sent_at             AS m_sent_at,
                    m.expires_at          AS m_expires_at,
                    m.sender_player_uuid  AS m_sender_player_uuid,
                    m.target_player_uuid  AS m_target_player_uuid
                FROM sb_mails m
                WHERE m.sender_player_uuid = ?
                ORDER BY m.sent_at DESC, m.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, senderPlayerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<MailDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapMail(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getOutbox failed for sender=" + senderPlayerUuid, e);
        }
    }

    /**
     * Unread inbox.
     */
    public List<MailDTO> getUnreadInbox(@NotNull String targetPlayerUuid) {
        final String sql = """
                SELECT
                    m.id                  AS m_id,
                    m.is_read             AS m_is_read,
                    m.subject             AS m_subject,
                    m.content             AS m_content,
                    m.read_at             AS m_read_at,
                    m.sent_at             AS m_sent_at,
                    m.expires_at          AS m_expires_at,
                    m.sender_player_uuid  AS m_sender_player_uuid,
                    m.target_player_uuid  AS m_target_player_uuid
                FROM sb_mails m
                WHERE m.target_player_uuid = ? AND m.is_read = 0
                ORDER BY m.sent_at DESC, m.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, targetPlayerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<MailDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapMail(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getUnreadInbox failed for target=" + targetPlayerUuid, e);
        }
    }

    // -- Update

    /**
     * Inbox between dates [fromIso, toIso].
     */
    public List<MailDTO> getInboxBetween(@NotNull String targetPlayerUuid, @NotNull String fromIso, @NotNull String toIso) {
        final String sql = """
                SELECT
                    m.id                  AS m_id,
                    m.is_read             AS m_is_read,
                    m.subject             AS m_subject,
                    m.content             AS m_content,
                    m.read_at             AS m_read_at,
                    m.sent_at             AS m_sent_at,
                    m.expires_at          AS m_expires_at,
                    m.sender_player_uuid  AS m_sender_player_uuid,
                    m.target_player_uuid  AS m_target_player_uuid
                FROM sb_mails m
                WHERE m.target_player_uuid = ?
                  AND m.sent_at >= ?
                  AND m.sent_at <= ?
                ORDER BY m.sent_at DESC, m.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, targetPlayerUuid);
            ps.setString(2, fromIso);
            ps.setString(3, toIso);
            try (ResultSet rs = ps.executeQuery()) {
                final List<MailDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapMail(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getInboxBetween failed for target=" + targetPlayerUuid, e);
        }
    }

    /**
     * Mark as read with timestamp.
     */
    public MailDTO markMailRead(long id, @NotNull String readAt) {
        final String sql = """
                UPDATE sb_mails
                SET is_read = 1, read_at = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, readAt);
            ps.setLong(2, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("markMailRead affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("markMailRead failed id=" + id, e);
        }
        return getMailById(id).orElseThrow(() -> new RuntimeException("markMailRead post-fetch missing id=" + id));
    }

    /**
     * Mark as unread and clear read_at.
     */
    public MailDTO markMailUnread(long id) {
        final String sql = """
                UPDATE sb_mails
                SET is_read = 0, read_at = NULL
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("markMailUnread affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("markMailUnread failed id=" + id, e);
        }
        return getMailById(id).orElseThrow(() -> new RuntimeException("markMailUnread post-fetch missing id=" + id));
    }

    /**
     * Bulk mark all unread mails for a target as read. Returns updated count.
     */
    public int markAllInboxRead(@NotNull String targetPlayerUuid, @NotNull String readAt) {
        final String sql = """
                UPDATE sb_mails
                SET is_read = 1, read_at = ?
                WHERE target_player_uuid = ? AND is_read = 0
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, readAt);
            ps.setString(2, targetPlayerUuid);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("markAllInboxRead failed for target=" + targetPlayerUuid, e);
        }
    }

    // -- Delete

    /**
     * Update subject and content, and optionally expires_at.
     */
    public MailDTO updateMail(long id, @NotNull String subject, @NotNull String content, String expiresAt) {
        final String sql = """
                UPDATE sb_mails
                SET subject = ?, content = ?, expires_at = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, subject);
            ps.setString(2, content);
            if (expiresAt == null) ps.setNull(3, Types.VARCHAR);
            else ps.setString(3, expiresAt);
            ps.setLong(4, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("updateMail affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("updateMail failed id=" + id, e);
        }
        return getMailById(id).orElseThrow(() -> new RuntimeException("updateMail post-fetch missing id=" + id));
    }

    /**
     * Delete by id and return previous row if it existed.
     */
    public Optional<MailDTO> deleteMailById(long id) {
        final Optional<MailDTO> before = getMailById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_mails
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteMailById failed id=" + id, e);
        }
    }

    // -- Mapper

    /**
     * Delete expired mails (expires_at <= nowIso). Returns affected count.
     */
    public int deleteExpired(@NotNull String nowIso) {
        final String sql = """
                DELETE FROM sb_mails
                WHERE expires_at IS NOT NULL AND expires_at <= ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nowIso);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("deleteExpired failed", e);
        }
    }
}
