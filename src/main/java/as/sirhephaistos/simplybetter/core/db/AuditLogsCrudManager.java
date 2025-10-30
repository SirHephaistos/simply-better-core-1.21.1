package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.AuditLogDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@SuppressWarnings("ClassCanBeRecord") // ABSOLUTELY NOT A RECORD CLASS - mutable state inside
public final class AuditLogsCrudManager {
    private final DatabaseManager db;

    public AuditLogsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    /**
     * Maps the current row to {@link AuditLogDTO}.
     */
    private static AuditLogDTO mapAuditLog(@NotNull ResultSet rs) throws SQLException {
        final long id = rs.getLong("id");
        final String tableName = rs.getString("table_name");
        final String initiator = rs.getString("initiator");
        final String contextJson = rs.getString("context_json");
        final String at = rs.getString("at");
        return new AuditLogDTO(id, tableName, initiator, contextJson, at);
    }

    /**
     * Inserts a new audit log row.
     *
     * @param log Input log; its {@code id} is ignored.
     * @return Persisted {@link AuditLogDTO} including generated {@code id}.
     * @throws RuntimeException on SQL errors or if no id is generated.
     */
    public AuditLogDTO createAuditLog(@NotNull AuditLogDTO log) {
        return createAuditLog(log.tableName(), log.initiator(), log.contextJson(), log.at());
    }

    /**
     * Inserts a new audit log row.
     *
     * @param tableName   Affected table.
     * @param initiator   Actor identifier.
     * @param contextJson JSON payload describing the event.
     * @param at          Event timestamp string.
     * @return Persisted {@link AuditLogDTO} including generated {@code id}.
     * @throws RuntimeException on SQL errors or if no id is generated.
     */
    public AuditLogDTO createAuditLog(@NotNull String tableName,
                                      @NotNull String initiator,
                                      @NotNull String contextJson,
                                      @NotNull String at) {
        final String sql = """
                INSERT INTO sb_audit_logs (table_name, initiator, context_json, at)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tableName);
            ps.setString(2, initiator);
            ps.setString(3, contextJson);
            ps.setString(4, at);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new RuntimeException("Failed to insert audit log: no generated key returned");
                }
                final long id = keys.getLong(1);
                return new AuditLogDTO(id, tableName, initiator, contextJson, at);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting audit log for table=" + tableName, e);
        }
    }

    /**
     * Retrieves an audit log by id.
     *
     * @param id Database id.
     * @return Optional containing the found {@link AuditLogDTO}, empty if none.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<AuditLogDTO> getAuditLogById(long id) {
        final String sql = """
                SELECT id, table_name, initiator, context_json, at
                FROM sb_audit_logs
                WHERE id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapAuditLog(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching audit log by id=" + id, e);
        }
    }

    /**
     * Lists all audit logs ordered by id ascending.
     *
     * @return List of {@link AuditLogDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public List<AuditLogDTO> getAllAuditLogs() {
        final String sql = """
                SELECT id, table_name, initiator, context_json, at
                FROM sb_audit_logs
                ORDER BY id ASC
                """;
        final List<AuditLogDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(mapAuditLog(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing audit logs", e);
        }
    }

    /**
     * Lists audit logs for a specific table.
     *
     * @param tableName Table name filter.
     * @param limit     Maximum rows to return. Use a positive value.
     * @return List of matching logs ordered by id descending.
     * @throws RuntimeException on SQL errors.
     */
    public List<AuditLogDTO> getAuditLogsForTable(@NotNull String tableName, int limit) {
        final String sql = """
                SELECT id, table_name, initiator, context_json, at
                FROM sb_audit_logs
                WHERE table_name = ?
                ORDER BY id DESC
                LIMIT ?
                """;
        final List<AuditLogDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setInt(2, Math.max(0, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapAuditLog(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing audit logs for table=" + tableName, e);
        }
    }

    /**
     * Lists audit logs created by a specific initiator.
     *
     * @param initiator Actor filter.
     * @param limit     Maximum rows to return. Use a positive value.
     * @return List of matching logs ordered by id descending.
     * @throws RuntimeException on SQL errors.
     */
    public List<AuditLogDTO> getAuditLogsByInitiator(@NotNull String initiator, int limit) {
        final String sql = """
                SELECT id, table_name, initiator, context_json, at
                FROM sb_audit_logs
                WHERE initiator = ?
                ORDER BY id DESC
                LIMIT ?
                """;
        final List<AuditLogDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, initiator);
            ps.setInt(2, Math.max(0, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapAuditLog(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing audit logs for initiator=" + initiator, e);
        }
    }

    /**
     * Lists audit logs in a time range.
     *
     * @param fromInclusive Lower bound for {@code at}, inclusive.
     * @param toExclusive   Upper bound for {@code at}, exclusive.
     * @param limit         Maximum rows to return. Use a positive value.
     * @return List of matching logs ordered by {@code at} ascending.
     * @throws RuntimeException on SQL errors.
     *                          // TODO: ensure {@code at} uses a lexicographically sortable format (e.g., ISO-8601).
     */
    public List<AuditLogDTO> getAuditLogsBetween(@NotNull String fromInclusive,
                                                 @NotNull String toExclusive,
                                                 int limit) {
        final String sql = """
                SELECT id, table_name, initiator, context_json, at
                FROM sb_audit_logs
                WHERE at >= ? AND at < ?
                ORDER BY at ASC
                LIMIT ?
                """;
        final List<AuditLogDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fromInclusive);
            ps.setString(2, toExclusive);
            ps.setInt(3, Math.max(0, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapAuditLog(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing audit logs between [" + fromInclusive + ", " + toExclusive + ")", e);
        }
    }

    // ------------------------- helpers -------------------------

    /**
     * Deletes an audit log by id.
     *
     * @param id Row id to delete.
     * @return Optional containing the pre-delete {@link AuditLogDTO}, empty if it did not exist.
     * @throws RuntimeException on SQL errors or if delete affects zero rows after existence check.
     */
    public Optional<AuditLogDTO> deleteAuditLogById(long id) {
        final Optional<AuditLogDTO> before = getAuditLogById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = "DELETE FROM sb_audit_logs WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            final int affected = ps.executeUpdate();
            if (affected == 0) throw new RuntimeException("No audit log deleted for id=" + id);
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting audit log id=" + id, e);
        }
    }
}
