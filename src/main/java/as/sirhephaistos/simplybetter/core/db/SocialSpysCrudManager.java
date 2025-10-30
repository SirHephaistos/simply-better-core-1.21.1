package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.SocialSpyDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_socialspy.
 * Primary key: spy_player_uuid.
 * Columns:
 * spy_player_uuid TEXT PRIMARY KEY,
 * created_at TEXT NOT NULL
 * // TODO: switch TEXT to TIMESTAMP if the schema uses real timestamps.
 */
public final class SocialSpysCrudManager {
    private final DatabaseManager db;

    public SocialSpysCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static SocialSpyDTO mapSocialSpy(ResultSet rs) throws SQLException {
        final String spyPlayerUuid = rs.getString("s_spy_player_uuid");
        final String createdAt = rs.getString("s_created_at");
        return new SocialSpyDTO(spyPlayerUuid, createdAt);
    }

    /**
     * Enable social spy for a player with DB default created_at.
     */
    public SocialSpyDTO createSocialSpy(@NotNull String spyPlayerUuid) {
        final String sql = """
                INSERT INTO sb_socialspy (spy_player_uuid)
                VALUES (?)
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, spyPlayerUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("createSocialSpy failed for player=" + spyPlayerUuid, e);
        }
        return getSocialSpyByPlayerUuid(spyPlayerUuid)
                .orElseThrow(() -> new RuntimeException("createSocialSpy post-fetch missing for player=" + spyPlayerUuid));
    }

    /**
     * Enable social spy with explicit created_at.
     */
    public SocialSpyDTO createSocialSpy(@NotNull String spyPlayerUuid, @NotNull String createdAt) {
        final String sql = """
                INSERT INTO sb_socialspy (spy_player_uuid, created_at)
                VALUES (?, ?)
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, spyPlayerUuid);
            ps.setString(2, createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("createSocialSpy(created_at) failed for player=" + spyPlayerUuid, e);
        }
        return getSocialSpyByPlayerUuid(spyPlayerUuid)
                .orElseThrow(() -> new RuntimeException("createSocialSpy(created_at) post-fetch missing for player=" + spyPlayerUuid));
    }

    // -- Read

    /**
     * Idempotent: create if missing, otherwise return existing.
     */
    public SocialSpyDTO createOrGetSocialSpy(@NotNull String spyPlayerUuid, @NotNull String createdAtIfNew) {
        final Optional<SocialSpyDTO> existing = getSocialSpyByPlayerUuid(spyPlayerUuid);
        if (existing.isPresent()) return existing.get();
        return createSocialSpy(spyPlayerUuid, createdAtIfNew);
    }

    /**
     * Get one by spy_player_uuid.
     */
    public Optional<SocialSpyDTO> getSocialSpyByPlayerUuid(@NotNull String spyPlayerUuid) {
        final String sql = """
                SELECT
                    s.spy_player_uuid AS s_spy_player_uuid,
                    s.created_at      AS s_created_at
                FROM sb_socialspy s
                WHERE s.spy_player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, spyPlayerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapSocialSpy(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getSocialSpyByPlayerUuid failed for player=" + spyPlayerUuid, e);
        }
    }

    /**
     * List all entries.
     */
    public List<SocialSpyDTO> getAllSocialSpies() {
        final String sql = """
                SELECT
                    s.spy_player_uuid AS s_spy_player_uuid,
                    s.created_at      AS s_created_at
                FROM sb_socialspy s
                ORDER BY s.spy_player_uuid
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<SocialSpyDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapSocialSpy(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllSocialSpies failed", e);
        }
    }

    // -- Update

    /**
     * Exists check.
     */
    public boolean existsSocialSpy(@NotNull String spyPlayerUuid) {
        final String sql = """
                SELECT 1
                FROM sb_socialspy
                WHERE spy_player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, spyPlayerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("existsSocialSpy failed for player=" + spyPlayerUuid, e);
        }
    }

    // -- Delete

    /**
     * Update created_at for an entry.
     */
    public SocialSpyDTO updateCreatedAt(@NotNull String spyPlayerUuid, @NotNull String createdAt) {
        final String sql = """
                UPDATE sb_socialspy
                SET created_at = ?
                WHERE spy_player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, createdAt);
            ps.setString(2, spyPlayerUuid);
            final int updated = ps.executeUpdate();
            if (updated == 0) throw new RuntimeException("updateCreatedAt affected 0 rows for player=" + spyPlayerUuid);
        } catch (SQLException e) {
            throw new RuntimeException("updateCreatedAt failed for player=" + spyPlayerUuid, e);
        }
        return getSocialSpyByPlayerUuid(spyPlayerUuid)
                .orElseThrow(() -> new RuntimeException("updateCreatedAt post-fetch missing for player=" + spyPlayerUuid));
    }

    // -- Mapper

    /**
     * Disable social spy and return the previous row if it existed.
     */
    public Optional<SocialSpyDTO> deleteSocialSpy(@NotNull String spyPlayerUuid) {
        final Optional<SocialSpyDTO> before = getSocialSpyByPlayerUuid(spyPlayerUuid);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_socialspy
                WHERE spy_player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, spyPlayerUuid);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteSocialSpy failed for player=" + spyPlayerUuid, e);
        }
    }
}
