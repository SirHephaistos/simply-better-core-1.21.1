package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.JailDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_jails.
 * Columns per JailDTO:
 * id BIGINT AUTO_INCREMENT PRIMARY KEY,
 * can_be_visited BOOLEAN NOT NULL,
 * center_position_id BIGINT NULL,
 * visit_entry_position_id BIGINT NULL
 * TODO: add FK constraints to sb_positions(id) and indexes on the FK columns if used for lookups.
 */
public final class JailsCrudManager {
    private final DatabaseManager db;

    public JailsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static JailDTO mapJail(ResultSet rs) throws SQLException {
        final long id = rs.getLong("j_id");
        final boolean canBeVisited = rs.getBoolean("j_can_be_visited");
        final long centerPos = rs.getLong("j_center_position_id");
        final Long centerPositionId = rs.wasNull() ? null : centerPos;
        final long visitPos = rs.getLong("j_visit_entry_position_id");
        final Long visitEntryPositionId = rs.wasNull() ? null : visitPos;
        return new JailDTO(id, canBeVisited, centerPositionId, visitEntryPositionId);
    }

    // -- Read

    /**
     * Insert a new jail. Returns the persisted row.
     */
    public JailDTO createJail(boolean canBeVisited, Long centerPositionId, Long visitEntryPositionId) {
        final String sql = """
                INSERT INTO sb_jails (can_be_visited, center_position_id, visit_entry_position_id)
                VALUES (?, ?, ?)
                """;
        long id;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setBoolean(1, canBeVisited);
            if (centerPositionId == null) ps.setNull(2, Types.BIGINT);
            else ps.setLong(2, centerPositionId);
            if (visitEntryPositionId == null) ps.setNull(3, Types.BIGINT);
            else ps.setLong(3, visitEntryPositionId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("createJail: no generated key");
                id = keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createJail failed", e);
        }
        return getJailById(id).orElseThrow(() -> new RuntimeException("createJail post-fetch missing id=" + id));
    }

    /**
     * Get by id.
     */
    public Optional<JailDTO> getJailById(long id) {
        final String sql = """
                SELECT
                    j.id                      AS j_id,
                    j.can_be_visited          AS j_can_be_visited,
                    j.center_position_id      AS j_center_position_id,
                    j.visit_entry_position_id AS j_visit_entry_position_id
                FROM sb_jails j
                WHERE j.id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapJail(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getJailById failed id=" + id, e);
        }
    }

    /**
     * List all jails.
     */
    public List<JailDTO> getAllJails() {
        final String sql = """
                SELECT
                    j.id                      AS j_id,
                    j.can_be_visited          AS j_can_be_visited,
                    j.center_position_id      AS j_center_position_id,
                    j.visit_entry_position_id AS j_visit_entry_position_id
                FROM sb_jails j
                ORDER BY j.id
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<JailDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapJail(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllJails failed", e);
        }
    }

    /**
     * Find jails by center_position_id.
     */
    public List<JailDTO> getJailsByCenterPositionId(Long centerPositionId) {
        final String sql = """
                SELECT
                    j.id                      AS j_id,
                    j.can_be_visited          AS j_can_be_visited,
                    j.center_position_id      AS j_center_position_id,
                    j.visit_entry_position_id AS j_visit_entry_position_id
                FROM sb_jails j
                WHERE (? IS NULL AND j.center_position_id IS NULL)
                   OR (? IS NOT NULL AND j.center_position_id = ?)
                ORDER BY j.id
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (centerPositionId == null) {
                ps.setNull(1, Types.BIGINT);
                ps.setNull(2, Types.BIGINT);
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setNull(1, Types.BIGINT);
                ps.setLong(2, centerPositionId);
                ps.setLong(3, centerPositionId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                final List<JailDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapJail(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getJailsByCenterPositionId failed", e);
        }
    }

    // -- Update

    /**
     * Find jails by visit_entry_position_id.
     */
    public List<JailDTO> getJailsByVisitEntryPositionId(Long visitEntryPositionId) {
        final String sql = """
                SELECT
                    j.id                      AS j_id,
                    j.can_be_visited          AS j_can_be_visited,
                    j.center_position_id      AS j_center_position_id,
                    j.visit_entry_position_id AS j_visit_entry_position_id
                FROM sb_jails j
                WHERE (? IS NULL AND j.visit_entry_position_id IS NULL)
                   OR (? IS NOT NULL AND j.visit_entry_position_id = ?)
                ORDER BY j.id
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (visitEntryPositionId == null) {
                ps.setNull(1, Types.BIGINT);
                ps.setNull(2, Types.BIGINT);
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setNull(1, Types.BIGINT);
                ps.setLong(2, visitEntryPositionId);
                ps.setLong(3, visitEntryPositionId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                final List<JailDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapJail(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getJailsByVisitEntryPositionId failed", e);
        }
    }

    /**
     * Update all mutable fields.
     */
    public JailDTO updateJail(long id, boolean canBeVisited, Long centerPositionId, Long visitEntryPositionId) {
        final String sql = """
                UPDATE sb_jails
                SET can_be_visited = ?, center_position_id = ?, visit_entry_position_id = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, canBeVisited);
            if (centerPositionId == null) ps.setNull(2, Types.BIGINT);
            else ps.setLong(2, centerPositionId);
            if (visitEntryPositionId == null) ps.setNull(3, Types.BIGINT);
            else ps.setLong(3, visitEntryPositionId);
            ps.setLong(4, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("updateJail affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("updateJail failed id=" + id, e);
        }
        return getJailById(id).orElseThrow(() -> new RuntimeException("updateJail post-fetch missing id=" + id));
    }

    /**
     * Toggle can_be_visited.
     */
    public JailDTO setJailVisitAllowed(long id, boolean canBeVisited) {
        final String sql = """
                UPDATE sb_jails
                SET can_be_visited = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, canBeVisited);
            ps.setLong(2, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setJailVisitAllowed affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("setJailVisitAllowed failed id=" + id, e);
        }
        return getJailById(id).orElseThrow(() -> new RuntimeException("setJailVisitAllowed post-fetch missing id=" + id));
    }

    /**
     * Update center_position_id.
     */
    public JailDTO setJailCenterPosition(long id, Long centerPositionId) {
        final String sql = """
                UPDATE sb_jails
                SET center_position_id = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (centerPositionId == null) ps.setNull(1, Types.BIGINT);
            else ps.setLong(1, centerPositionId);
            ps.setLong(2, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setJailCenterPosition affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("setJailCenterPosition failed id=" + id, e);
        }
        return getJailById(id).orElseThrow(() -> new RuntimeException("setJailCenterPosition post-fetch missing id=" + id));
    }

    // -- Delete

    /**
     * Update visit_entry_position_id.
     */
    public JailDTO setJailVisitEntryPosition(long id, Long visitEntryPositionId) {
        final String sql = """
                UPDATE sb_jails
                SET visit_entry_position_id = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (visitEntryPositionId == null) ps.setNull(1, Types.BIGINT);
            else ps.setLong(1, visitEntryPositionId);
            ps.setLong(2, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setJailVisitEntryPosition affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("setJailVisitEntryPosition failed id=" + id, e);
        }
        return getJailById(id).orElseThrow(() -> new RuntimeException("setJailVisitEntryPosition post-fetch missing id=" + id));
    }

    // -- Mapper

    /**
     * Delete by id and return prior row if it existed.
     */
    public Optional<JailDTO> deleteJailById(long id) {
        final Optional<JailDTO> before = getJailById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_jails
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteJailById failed id=" + id, e);
        }
    }
}
