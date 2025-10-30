package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.PositionDTO;
import as.sirhephaistos.simplybetter.library.WarpDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WarpsCrudManager {
    private final DatabaseManager db; // provides connections

    public WarpsCrudManager(DatabaseManager db) {
        this.db = db;
    }

    /**
     * Helper to construct WarpDTO from ResultSet
     *
     * @param rs ResultSet positioned at a valid row
     * @return WarpDTO constructed from the current row
     * @throws SQLException on SQL errors
     */
    private WarpDTO constructWarpDTO(ResultSet rs) throws SQLException {
        PositionDTO pos = new PositionDTO(
                rs.getString("p_dimension_id"),
                rs.getDouble("p_x"),
                rs.getDouble("p_y"),
                rs.getDouble("p_z"),
                rs.getFloat("p_yaw"),
                rs.getFloat("p_pitch")
        );
        WarpDTO warp = new WarpDTO(
                rs.getLong("w_id"),
                rs.getString("w_name"),
                pos,
                rs.getString("w_created_at"),
                rs.getString("w_created_by_uuid")
        );
        return warp;
    }

    /**
     * Create a new warp
     */
    public WarpDTO createWarp(String name, String createdByUuid, @NotNull PositionDTO position) {
        final String insertPositionSql = """
                INSERT INTO sb_positions (dimension_id, x, y, z, orientation_yaw, orientation_pitch)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        final String insertWarpSql = """
                INSERT INTO sb_warps (name, created_by_uuid, position_id)
                VALUES (?, ?, ?)
                """;

        final String selectByIdSql = """
                SELECT
                    w.id                      AS w_id,
                    w.name                    AS w_name,
                    w.created_by_uuid         AS w_created_by_uuid,
                    w.created_at              AS w_created_at,
                    p.dimension_id            AS p_dimension_id,
                    p.x                       AS p_x,
                    p.y                       AS p_y,
                    p.z                       AS p_z,
                    p.orientation_yaw         AS p_yaw,
                    p.orientation_pitch       AS p_pitch
                FROM sb_warps w
                JOIN sb_positions p ON w.position_id = p.id
                WHERE w.id = ?
                """;

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            //1 Insert position
            long positionId; // to hold generated position ID
            try (PreparedStatement posStmt =
                         conn.prepareStatement(insertPositionSql, Statement.RETURN_GENERATED_KEYS)) {
                posStmt.setString(1, position.dimensionId());
                posStmt.setDouble(2, position.x());
                posStmt.setDouble(3, position.y());
                posStmt.setDouble(4, position.z());
                posStmt.setFloat(5, position.yaw());
                posStmt.setFloat(6, position.pitch());
                posStmt.executeUpdate();
                try (ResultSet keys = posStmt.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No position ID generated");
                    positionId = keys.getLong(1);
                }
            }

            //2 Insert warp
            long warpId; // to hold generated warp ID
            try (PreparedStatement warpStmt =
                         conn.prepareStatement(insertWarpSql, Statement.RETURN_GENERATED_KEYS)) {
                warpStmt.setString(1, name);
                warpStmt.setString(2, createdByUuid);
                warpStmt.setLong(3, positionId);
                warpStmt.executeUpdate();
                try (ResultSet keys = warpStmt.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No warp ID generated");
                    warpId = keys.getLong(1);
                }
            }

            //3 Retrieve inserted warp
            WarpDTO result; // to hold the result
            try (PreparedStatement sel = conn.prepareStatement(selectByIdSql)) {
                sel.setLong(1, warpId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Inserted warp not found");

                    PositionDTO pos = new PositionDTO(
                            rs.getString("p_dimension_id"),
                            rs.getDouble("p_x"),
                            rs.getDouble("p_y"),
                            rs.getDouble("p_z"),
                            rs.getFloat("p_yaw"),
                            rs.getFloat("p_pitch")
                    );
                    result = new WarpDTO(
                            rs.getLong("w_id"),
                            rs.getString("w_name"),
                            pos,
                            rs.getString("w_created_at"),
                            rs.getString("w_created_by_uuid")
                    );
                }
            }

            conn.commit();
            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Error creating warp", e);
        }
    }

    /**
     * Get a warp by unique name
     *
     * @param name Warp name
     * @return Optional of WarpDTO if found, else empty
     */
    public Optional<WarpDTO> getWarpByName(String name) {
        final String selectByNameSql = """
                SELECT
                    w.id                      AS w_id,
                    w.name                    AS w_name,
                    w.created_by_uuid         AS w_created_by_uuid,
                    w.created_at              AS w_created_at,
                    p.dimension_id            AS p_dimension_id,
                    p.x                       AS p_x,
                    p.y                       AS p_y,
                    p.z                       AS p_z,
                    p.orientation_yaw         AS p_yaw,
                    p.orientation_pitch       AS p_pitch
                FROM sb_warps w
                JOIN sb_positions p ON w.position_id = p.id
                WHERE w.name = ?
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectByNameSql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(constructWarpDTO(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving warp by name", e);
        }
    }

    /**
     * List warps by creator UUID
     *
     * @param createdBy Creator UUID
     * @return List of WarpDTOs created by the specified UUID
     *
     */
    public List<WarpDTO> getWarpsByCreator(String createdBy) {
        final String selectByCreatorSql = """
                SELECT
                    w.id                      AS w_id,
                    w.name                    AS w_name,
                    w.created_by_uuid         AS w_created_by_uuid,
                    w.created_at              AS w_created_at,
                    p.dimension_id            AS p_dimension_id,
                    p.x                       AS p_x,
                    p.y                       AS p_y,
                    p.z                       AS p_z,
                    p.orientation_yaw         AS p_yaw,
                    p.orientation_pitch       AS p_pitch
                FROM sb_warps w
                JOIN sb_positions p ON w.position_id = p.id
                WHERE w.created_by_uuid = ?
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectByCreatorSql)) {
            stmt.setString(1, createdBy);
            try (ResultSet rs = stmt.executeQuery()) {
                List<WarpDTO> warps = new ArrayList<>();
                while (rs.next()) {
                    warps.add(constructWarpDTO(rs));
                }
                return warps;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving warps by creator", e);
        }
    }


    /**
     * List all warps
     */
    public List<WarpDTO> getAllWarps() {
        final String selectAllSql = """
                SELECT
                    w.id                      AS w_id,
                    w.name                    AS w_name,
                    w.created_by_uuid         AS w_created_by_uuid,
                    w.created_at              AS w_created_at,
                    p.dimension_id            AS p_dimension_id,
                    p.x                       AS p_x,
                    p.y                       AS p_y,
                    p.z                       AS p_z,
                    p.orientation_yaw         AS p_yaw,
                    p.orientation_pitch       AS p_pitch
                FROM sb_warps w
                JOIN sb_positions p ON w.position_id = p.id
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectAllSql);
             ResultSet rs = stmt.executeQuery()) {
            List<WarpDTO> warps = new ArrayList<>();
            while (rs.next()) {
                warps.add(constructWarpDTO(rs));
            }
            return warps;
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving all warps", e);
        }
    }

    /**
     * Update warp position
     *
     * @param name        Warp name
     * @param newPosition New position
     * @return Updated WarpDTO
     */
    public WarpDTO updateWarpPosition(String name, PositionDTO newPosition) {
        final String sql = """
                    UPDATE sb_positions
                    SET dimension_id = ?,
                        x = ?, y = ?, z = ?,
                        orientation_yaw = ?, orientation_pitch = ?
                    WHERE id = (SELECT position_id FROM sb_warps WHERE name = ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPosition.dimensionId());
            stmt.setDouble(2, newPosition.x());
            stmt.setDouble(3, newPosition.y());
            stmt.setDouble(4, newPosition.z());
            stmt.setFloat(5, newPosition.yaw());
            stmt.setFloat(6, newPosition.pitch());
            stmt.setString(7, name);
            if (stmt.executeUpdate() == 0) throw new RuntimeException("No warp found: " + name);
            return getWarpByName(name).orElseThrow(() -> new RuntimeException("Warp not found after update: " + name));
        } catch (SQLException e) {
            throw new RuntimeException("Error updating warp position", e);
        }
    }


    /**
     * Rename a warp
     *
     * @param oldName the current name of the warp
     * @param newName the new name for the warp
     * @return the updated WarpDTO
     */
    public WarpDTO renameWarp(String oldName, String newName) {
        final String renameWarpSql = """
                UPDATE sb_warps
                SET name = ?
                WHERE name = ?
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(renameWarpSql)) {
            stmt.setString(1, newName);
            stmt.setString(2, oldName);
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new RuntimeException("No warp found with name: " + oldName);
            }
            return getWarpByName(newName).orElseThrow(() -> new RuntimeException("Warp not found after rename: " + newName));
        } catch (SQLException e) {
            throw new RuntimeException("Error renaming warp", e);
        }
    }

    /**
     * Delete warp and return deleted entity
     *
     * @param name Warp name
     * @return Optional of deleted WarpDTO, or empty if not found
     */
    public Optional<WarpDTO> deleteWarp(String name) {
        final String sql = "DELETE FROM sb_warps WHERE name = ?";
        Optional<WarpDTO> before = getWarpByName(name);
        if (before.isEmpty()) return Optional.empty();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            if (stmt.executeUpdate() == 0) throw new RuntimeException("No warp deleted: " + name);
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting warp", e);
        }
    }
}
