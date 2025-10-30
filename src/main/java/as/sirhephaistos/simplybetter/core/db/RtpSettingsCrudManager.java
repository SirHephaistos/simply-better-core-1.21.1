package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.RtpSettingsDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD manager for {@link RtpSettingsDTO}.
 * <p>
 * Persists rows in table {@code sb_rtp_settings} with columns:
 * <ul>
 *   <li>world_id INTEGER PRIMARY KEY REFERENCES sb_worlds(id) ON DELETE CASCADE</li>
 *   <li>min_range INTEGER NOT NULL</li>
 *   <li>max_range INTEGER NOT NULL</li>
 *   <li>cooldown_seconds INTEGER NOT NULL</li>
 * </ul>
 * All SQLExceptions are wrapped in RuntimeExceptions with context.
 */
public final class RtpSettingsCrudManager {
    private final DatabaseManager db;

    /**
     * Creates a new CRUD manager for RTP settings.
     *
     * @param db Database manager providing JDBC connections.
     */
    public RtpSettingsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    private static long requireWorldId(Long id) {
        if (id == null) throw new IllegalArgumentException("worldId must not be null");
        return id;
    }

    private static RtpSettingsDTO map(@NotNull ResultSet rs) throws SQLException {
        final long worldId = rs.getLong("world_id");
        final int minRange = rs.getInt("min_range");
        final int maxRange = rs.getInt("max_range");
        final int cooldownSeconds = rs.getInt("cooldown_seconds");
        return new RtpSettingsDTO(worldId, minRange, maxRange, cooldownSeconds);
    }

    /**
     * Inserts RTP settings for a world.
     *
     * @param s Settings to insert. {@code worldId} must reference an existing world.
     * @return Persisted {@link RtpSettingsDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public RtpSettingsDTO createRtpSettings(@NotNull RtpSettingsDTO s) {
        final String sql = """
                INSERT INTO sb_rtp_settings (world_id, min_range, max_range, cooldown_seconds)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, requireWorldId(s.worldId()));
            ps.setInt(2, s.minRange());
            ps.setInt(3, s.maxRange());
            ps.setInt(4, s.cooldownSeconds());
            ps.executeUpdate();
            return s;
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting RTP settings for world_id=" + s.worldId(), e);
        }
    }

    /**
     * Retrieves RTP settings by {@code world_id}.
     *
     * @param worldId World id.
     * @return Optional containing the found settings or empty if none.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<RtpSettingsDTO> getRtpSettingsByWorldId(long worldId) {
        final String sql = """
                SELECT world_id, min_range, max_range, cooldown_seconds
                FROM sb_rtp_settings
                WHERE world_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, worldId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching RTP settings for world_id=" + worldId, e);
        }
    }

    /**
     * Lists RTP settings for all worlds.
     *
     * @return List of {@link RtpSettingsDTO}.
     * @throws RuntimeException on SQL errors.
     */
    public List<RtpSettingsDTO> getAllRtpSettings() {
        final String sql = """
                SELECT world_id, min_range, max_range, cooldown_seconds
                FROM sb_rtp_settings
                ORDER BY world_id ASC
                """;
        final List<RtpSettingsDTO> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing RTP settings", e);
        }
    }

    /**
     * Updates RTP settings for a world.
     *
     * @param worldId World id.
     * @param s       New values. {@code worldId} inside the DTO is ignored.
     * @return Optional containing the updated settings or empty if the row did not exist.
     * @throws RuntimeException on SQL errors or unexpected empty fetch after update.
     */
    public Optional<RtpSettingsDTO> updateRtpSettings(long worldId, @NotNull RtpSettingsDTO s) {
        if (getRtpSettingsByWorldId(worldId).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_rtp_settings
                SET min_range = ?, max_range = ?, cooldown_seconds = ?
                WHERE world_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, s.minRange());
            ps.setInt(2, s.maxRange());
            ps.setInt(3, s.cooldownSeconds());
            ps.setLong(4, worldId);
            final int updated = ps.executeUpdate();
            if (updated == 0) throw new RuntimeException("No RTP settings updated for world_id=" + worldId);

            return getRtpSettingsByWorldId(worldId).map(x -> x).or(() -> {
                throw new RuntimeException("Updated RTP settings not found for world_id=" + worldId);
            });
        } catch (SQLException e) {
            throw new RuntimeException("Error updating RTP settings for world_id=" + worldId, e);
        }
    }

    /**
     * Deletes RTP settings for a world.
     *
     * @param worldId World id to delete.
     * @return Optional containing the pre-delete settings or empty if none existed.
     * @throws RuntimeException on SQL errors or if delete affects zero rows after existence check.
     */
    public Optional<RtpSettingsDTO> deleteRtpSettingsByWorldId(long worldId) {
        final Optional<RtpSettingsDTO> before = getRtpSettingsByWorldId(worldId);
        if (before.isEmpty()) return Optional.empty();

        final String sql = "DELETE FROM sb_rtp_settings WHERE world_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, worldId);
            final int affected = ps.executeUpdate();
            if (affected == 0) throw new RuntimeException("No RTP settings deleted for world_id=" + worldId);
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting RTP settings for world_id=" + worldId, e);
        }
    }

    // ------------------------- helpers -------------------------

    /**
     * Convenience update for range only.
     *
     * @param worldId  World id.
     * @param minRange Minimum range.
     * @param maxRange Maximum range.
     * @return Optional containing the updated settings or empty if the row did not exist.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<RtpSettingsDTO> setRange(long worldId, int minRange, int maxRange) {
        if (getRtpSettingsByWorldId(worldId).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_rtp_settings
                SET min_range = ?, max_range = ?
                WHERE world_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, minRange);
            ps.setInt(2, maxRange);
            ps.setLong(3, worldId);
            ps.executeUpdate();
            return getRtpSettingsByWorldId(worldId);
        } catch (SQLException e) {
            throw new RuntimeException("Error setting range for world_id=" + worldId, e);
        }
    }

    /**
     * Convenience update for cooldown only.
     *
     * @param worldId         World id.
     * @param cooldownSeconds New cooldown seconds.
     * @return Optional containing the updated settings or empty if the row did not exist.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<RtpSettingsDTO> setCooldown(long worldId, int cooldownSeconds) {
        if (getRtpSettingsByWorldId(worldId).isEmpty()) return Optional.empty();

        final String sql = """
                UPDATE sb_rtp_settings
                SET cooldown_seconds = ?
                WHERE world_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cooldownSeconds);
            ps.setLong(2, worldId);
            ps.executeUpdate();
            return getRtpSettingsByWorldId(worldId);
        } catch (SQLException e) {
            throw new RuntimeException("Error setting cooldown for world_id=" + worldId, e);
        }
    }
}
