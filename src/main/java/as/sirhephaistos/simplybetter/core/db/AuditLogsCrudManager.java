// java
package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.AuditLogDTO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <h1><img src="https://docs.godsmg.com/~gitbook/image?url=https%3A%2F%2F602320278-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_hKBWF%252Ficon%252FF3ga5TrIrIMXtWecHo3z%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252017_44_38.png%3Falt%3Dmedia%26token%3D8c3f45e4-ed6f-47ab-a4ab-474d24fa3bb3&width=32&dpr=1&quality=100&sign=2c456f01&sv=2"></img>
 * &nbsp;CRUD manager for {@link AuditLogDTO}
 * <img src="https://docs-sbs.godsmg.com/~gitbook/image?url=https%3A%2F%2F655127117-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_ofAiW%252Ficon%252F9SRBPTo3OKBsw5DvBwL3%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252000_07_28.png%3Falt%3Dmedia%26token%3D396dda36-5693-4638-b53e-59bf0770f309&width=32&dpr=1&quality=100&sign=55c114e6&sv=2"></img> </h1>
 * <h2>Create Methods</h2>
 * <ul>
 *     <li>{@link #createAuditLog(String, String, String, String)}:</br>
 *         Create a new audit log row. Returns the inserted {@link AuditLogDTO} including the generated id.
 *     </li>
 * </ul>
 * <h2>Read Methods</h2>
 * <ul>
 *     <li>{@link #getAuditLogById(long)}:</br>
 *         Get an audit log by id. Returns an {@link Optional} containing {@link AuditLogDTO} if found, empty otherwise.</li>
 *     <li>{@link #getAllAuditLogs}:</br>
 *         Return a list of all audit logs in the database. Returns a {@link List} of {@link AuditLogDTO}.</li>
 *     <li>{@link #getAuditLogsForTable(String, int)}:</br>
 *         Return recent logs for a specific table, limited by the given value.</li>
 *     <li>{@link #getAuditLogsByInitiator(String, int)}:</br>
 *         Return recent logs created by a specific initiator, limited by the given value.</li>
 *     <li>{@link #getAuditLogsBetween(String, String, int)}:</br>
 *         Return logs between two timestamp strings (inclusive/exclusive), limited by the given value.</li>
 * </ul>
 * <h2>Update Methods</h2>
 * <ul>
 *     <li>{@link #updateAuditLog()}:</br>
 *     Audit logs are immutable and cannot be updated once created.
 *     This method always throws {@link UnsupportedOperationException}.</li>
 * </ul>
 * <h2>Delete Methods</h2>
 * <ul>
 *     <li>{@link #deleteAuditLogById(long)}:</br>
 *     Delete an audit log by id and return the deleted record if it existed.</li>
 * </ul>
 *
 * <h3>General Information</h3>
 * @codeBaseStatus Complete
 * @testingStatus AwaitingJUnitTests
 * @author Sirhephaistos
 * @version 1.0
 */
@SuppressWarnings("ClassCanBeRecord") // ABSOLUTELY NOT A RECORD CLASS - mutable state inside
public final class AuditLogsCrudManager {
    private final DatabaseManager db;

    public AuditLogsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    /**
     * Private helper to get a mounted {@link AuditLogDTO} from a {@link ResultSet}.
     * @param rs {@link ResultSet} positioned at the desired row.
     * @return The mapped {@link AuditLogDTO}.
     * @throws SQLException If a database access error occurs.
     * @throws IllegalArgumentException If the ResultSet is null.
     * @throws IllegalStateException If required columns are missing or the ResultSet is not positioned on a valid row.
     */
    private static AuditLogDTO mapAuditLog( ResultSet rs) throws SQLException {
        if (rs == null) throw new IllegalArgumentException("mapAuditLog: ResultSet cannot be null");
        if (rs.getLong("id") < 1)
            throw new IllegalStateException("mapAuditLog: ResultSet is not positioned on a valid row");
        if (rs.getString("table_name") == null)
            throw new IllegalStateException("mapAuditLog: ResultSet is missing required column: table_name");
        if (rs.getString("at") == null)
            throw new IllegalStateException("mapAuditLog: ResultSet is missing required column: at");
        final long id = rs.getLong("id");
        final String tableName = rs.getString("table_name");
        final String initiator = rs.getString("initiator");
        final String contextJson = rs.getString("context_json");
        final String at = rs.getString("at");
        return new AuditLogDTO(id, tableName, initiator, contextJson, at);
    }

    // -- Create

    /**
     * Create a new audit log row.
     * @param tableName The table name being audited.
     * @param initiator Who initiated the change.
     * @param contextJson JSON context describing the change.
     * @param at Timestamp string for the event.
     * @return Created {@link AuditLogDTO} including generated id.
     * @throws RuntimeException on SQL errors.
     */
    public AuditLogDTO createAuditLog(@NotNull String tableName,
                                      @Nullable String initiator,
                                      @Nullable String contextJson,
                                      @NotNull String at) {
        final String sql = """
                INSERT INTO sb_audit_logs (table_name, initiator, context_json, at)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tableName);
            if (initiator != null) {
                ps.setString(2, initiator);
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            if (contextJson != null) { ps.setString(3, contextJson);
            } else {
                ps.setNull(3, Types.VARCHAR);
            }
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

    // -- Read
    /**
     * Get an audit log by id.
     * @param id Identifier of the audit log.
     * @return an {@link Optional} containing {@link AuditLogDTO} if found, empty otherwise.
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
     * Return a list of all audit logs in the database.
     * @return a {@link List} of {@link AuditLogDTO}.
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
     * Return recent audit logs for a specific table.
     * @param tableName Table name to filter by.
     * @param limit Maximum number of logs to return.
     * @return a {@link List} of {@link AuditLogDTO}.
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
     * Return recent audit logs created by a specific initiator.
     * @param initiator Initiator identifier.
     * @param limit Maximum number of logs to return.
     * @return a {@link List} of {@link AuditLogDTO}.
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
     * Return audit logs between two timestamps.
     * @param fromInclusive Start timestamp (inclusive).
     * @param toExclusive End timestamp (exclusive).
     * @param limit Maximum number of logs to return. Set it to -1 for no limit.
     * @return a {@link List} of {@link AuditLogDTO}.
     * @throws RuntimeException on SQL errors.
     * @throws IllegalArgumentException if limit is zero or less than -1.
     */
    public List<AuditLogDTO> getAuditLogsBetween(@NotNull String fromInclusive,
                                                 @NotNull String toExclusive,
                                                 int limit) {
        if (limit == 0|| limit < -1) {
            throw new IllegalArgumentException("Limit must be positive or -1 for no limit");
        }
        String sql = """
                SELECT id, table_name, initiator, context_json, at
                FROM sb_audit_logs
                WHERE at >= ? AND at < ?
                ORDER BY at ASC""";

        if (limit ==1) {
            // Append LIMIT clause if limit is non-negative
            sql += " LIMIT ?";
        }
        final List<AuditLogDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fromInclusive);
            ps.setString(2, toExclusive);
            if (limit == 1) {
                ps.setInt(3, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapAuditLog(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing audit logs between [" + fromInclusive + ", " + toExclusive + ")", e);
        }
    }

    // -- Update
    /**
     * Audit logs are immutable and cannot be updated once created.
     * This method always throws {@link UnsupportedOperationException}.
     * @throws UnsupportedOperationException always.
     */
    public void updateAuditLog() {
        throw new UnsupportedOperationException("Audit logs cannot be updated once created.");
    }
    // -- Delete
    /**
     * Delete an audit log by id.
     * @param id Identifier of the audit log to delete.
     * @throws IllegalArgumentException if no audit log exists for the given id.
     * @throws RuntimeException on SQL errors or if no rows were affected.
     */
    public void deleteAuditLogById(long id) {
        if (getAuditLogById(id).isEmpty()) {
            throw new IllegalArgumentException("deleteAuditLogById: No audit log found for id=" + id);
        }
        final String sql = "DELETE FROM sb_audit_logs WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            if (ps.getUpdateCount() == 0) {
                throw new RuntimeException("deleteAuditLogById: affected 0 rows for id=" + id);
            }
            if (getAuditLogById(id).isPresent()) {
                throw new RuntimeException("deleteAuditLogById: failed to delete for id=" + id);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting audit log id=" + id, e);
        }
    }
}