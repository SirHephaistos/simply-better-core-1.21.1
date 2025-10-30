package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.PlayerSettingsDTO;
import net.minecraft.server.command.PublishCommand;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class PlayerSettingsCrudManager {
    private final DatabaseManager db;

    public PlayerSettingsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // Utility methods

    /**
     * Constructs a PlayerSettingsDTO from the current row of the given ResultSet.
     *
     * @param rs The ResultSet positioned at the desired row.
     * @return A PlayerSettingsDTO populated with data from the ResultSet.
     * @throws SQLException If an SQL error occurs while accessing the ResultSet.
     */
    private PlayerSettingsDTO constructPlayerSettingsDTO(ResultSet rs) throws SQLException {
        return new PlayerSettingsDTO(
                rs.getString("player_uuid"),
                rs.getBoolean("tpa_blocked"),
                rs.getBoolean("msg_blocked"),
                rs.getBoolean("auto_tp_accept"),
                rs.getBoolean("pay_blocked"),
                rs.getBoolean("pay_confirm"),
                rs.getBoolean("clearinv_confirm"),
                rs.getBoolean("vanished")
        );
    }

    // ----------- CRUD methods -----------

    // Read methods
    /**
     * Retrieves the player settings for a given player UUID.
     *
     * @param uuid The UUID of the player whose settings are to be retrieved.
     * @return An Optional containing the PlayerSettingsDTO if found, or empty if not found.
     */
    public Optional<PlayerSettingsDTO> getPlayerSettingsByUUID(@NotNull String uuid) {
        final String selectByPlayerUuid = "SELECT * FROM player_settings ps WHERE ps.player_uuid = ?;";

        try (var ps = db.getConnection().prepareStatement(selectByPlayerUuid)) {
            ps.setString(1, uuid);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(constructPlayerSettingsDTO(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get player settings by UUID", e);
        }

    }

    // Create methods

    /**
     * Creates a new player settings entry for the given player UUID.
     *
     * @param uuid The UUID of the player for whom to create settings.
     * @return The newly created PlayerSettingsDTO.
     */
    public PlayerSettingsDTO createPlayerSettings(@NotNull String uuid) {
        final String insertPlayerSettings = "INSERT INTO player_settings (player_uuid) VALUES (?);";

        try (var ps = db.getConnection().prepareStatement(insertPlayerSettings)) {
            ps.setString(1, uuid);
            ps.executeUpdate();
            return getPlayerSettingsByUUID(uuid).orElseThrow(() -> new RuntimeException("Failed to create player settings by UUID"));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create player settings", e);
        }
    }

    // Update methods

    /**
     * Updates the TPA blocked setting for a given player UUID.
     *
     * @param uuid       The UUID of the player whose TPA blocked setting is to be updated.
     * @param tpaBlocked The new value for the TPA blocked setting.
     * @return The updated PlayerSettingsDTO.
     */
    public PlayerSettingsDTO updatePlayerSettingsTpaBlocked(@NotNull String uuid, boolean tpaBlocked) {
        final String updateTpaBlocked = "UPDATE player_settings SET tpa_blocked = ? WHERE player_uuid = ?;";

        try (var ps = db.getConnection().prepareStatement(updateTpaBlocked)) {
            ps.setBoolean(1, tpaBlocked);
            ps.setString(2, uuid);
            ps.executeUpdate();
            return getPlayerSettingsByUUID(uuid).orElseThrow(() -> new RuntimeException("Failed to get player settings by UUID after update"));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update player TPA blocked setting", e);
        }
    }

    /**
     * Updates the MSG blocked setting for a given player UUID.
     *
     * @param uuid       The UUID of the player whose MSG blocked setting is to be updated.
     * @param msgBlocked The new value for the MSG blocked setting.
     * @return The updated PlayerSettingsDTO.
     */
    public PlayerSettingsDTO updatePlayerSettingsMsgBlocked(@NotNull String uuid, boolean msgBlocked) {
        final String updateMsgBlocked = "UPDATE player_settings SET msg_blocked = ? WHERE player_uuid = ?;";

        try (var ps = db.getConnection().prepareStatement(updateMsgBlocked)) {
            ps.setBoolean(1, msgBlocked);
            ps.setString(2, uuid);
            ps.executeUpdate();
            return getPlayerSettingsByUUID(uuid).orElseThrow(() -> new RuntimeException("Failed to get player settings by UUID after update"));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update player MSG blocked setting", e);
        }
    }

    /**
     * Updates the Auto TP Accept setting for a given player UUID.
     *
     * @param uuid          The UUID of the player whose Auto TP Accept setting is to be updated.
     * @param autoTpAccept The new value for the Auto TP Accept setting.
     * @return The updated PlayerSettingsDTO.
     */
    public PlayerSettingsDTO updatePlayerSettingsAutoTpAccept(@NotNull String uuid, boolean autoTpAccept) {
        final String updateAutoTpAccept = "UPDATE player_settings SET auto_tp_accept = ? WHERE player_uuid = ?;";

        try (var ps = db.getConnection().prepareStatement(updateAutoTpAccept)) {
            ps.setBoolean(1, autoTpAccept);
            ps.setString(2, uuid);
            ps.executeUpdate();
            return getPlayerSettingsByUUID(uuid).orElseThrow(() -> new RuntimeException("Failed to get player settings by UUID after update"));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update player Auto TP Accept setting", e);
        }
    }

    /**
     * Updates the Pay blocked setting for a given player UUID.
     *
     * @param uuid       The UUID of the player whose Pay blocked setting is to be updated.
     * @param payBlocked The new value for the Pay blocked setting.
     * @return The updated PlayerSettingsDTO.
     */
    public PlayerSettingsDTO updatePlayerSettingsPayBlocked(@NotNull String uuid, boolean payBlocked) {
        final String updatePayBlocked = "UPDATE player_settings SET pay_blocked = ? WHERE player_uuid = ?;";

        try (var ps = db.getConnection().prepareStatement(updatePayBlocked)) {
            ps.setBoolean(1, payBlocked);
            ps.setString(2, uuid);
            ps.executeUpdate();
            return getPlayerSettingsByUUID(uuid).orElseThrow(() -> new RuntimeException("Failed to get player settings by UUID after update"));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update player Pay blocked setting", e);
        }
    }
    /**
     * Updates the Pay confirm setting for a given player UUID.
     *
     * @param uuid       The UUID of the player whose Pay confirm setting is to be updated.
     * @param payConfirm The new value for the Pay confirm setting.
     * @return The updated PlayerSettingsDTO.
     */
    public PlayerSettingsDTO updatePlayerSettingsPayConfirm(@NotNull String uuid, boolean payConfirm) {
        final String updatePayConfirm = "UPDATE player_settings SET pay_confirm = ? WHERE player_uuid = ?;";

        try (var ps = db.getConnection().prepareStatement(updatePayConfirm)) {
            ps.setBoolean(1, payConfirm);
            ps.setString(2, uuid);
            ps.executeUpdate();
            return getPlayerSettingsByUUID(uuid).orElseThrow(() -> new RuntimeException("Failed to get player settings by UUID after update"));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update player Pay confirm setting", e);
        }
    }
    /**
     * Updates the Clear Inventory confirm setting for a given player UUID.
     *
     * @param uuid             The UUID of the player whose Clear Inventory confirm setting is to be updated.
     * @param clearInvConfirm The new value for the Clear Inventory confirm setting.
     * @return The updated PlayerSettingsDTO.
     */
    public PlayerSettingsDTO updatePlayerSettingsClearInvConfirm(@NotNull String uuid, boolean clearInvConfirm) {
        final String updateClearInvConfirm = "UPDATE player_settings SET clearinv_confirm = ? WHERE player_uuid = ?;";

        try (var ps = db.getConnection().prepareStatement(updateClearInvConfirm)) {
            ps.setBoolean(1, clearInvConfirm);
            ps.setString(2, uuid);
            ps.executeUpdate();
            return getPlayerSettingsByUUID(uuid).orElseThrow(() -> new RuntimeException("Failed to get player settings by UUID after update"));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update player Clear Inventory confirm setting", e);
        }
    }

    /**
     * Updates the Vanished setting for a given player UUID.
     *
     * @param uuid     The UUID of the player whose Vanished setting is to be updated.
     * @param vanished The new value for the Vanished setting.
     * @return The updated PlayerSettingsDTO.
     */
    public PlayerSettingsDTO updatePlayerSettingsVanished(@NotNull String uuid, boolean vanished) {
        final String updateVanished = "UPDATE player_settings SET vanished = ? WHERE player_uuid = ?;";

        try (var ps = db.getConnection().prepareStatement(updateVanished)) {
            ps.setBoolean(1, vanished);
            ps.setString(2, uuid);
            ps.executeUpdate();
            return getPlayerSettingsByUUID(uuid).orElseThrow(() -> new RuntimeException("Failed to get player settings by UUID after update"));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update player Vanished setting", e);
        }
    }

    // Delete methods
    /**
     * Deletes the player settings for a given player UUID.
     *
     * @param uuid The UUID of the player whose settings are to be deleted.
     * @return An Optional containing the deleted PlayerSettingsDTO if found, or empty if not found.
     */
    public Optional<PlayerSettingsDTO> deletePlayerSettingsByUUID(@NotNull String uuid) {
        final String deleteByPlayerUuid = "DELETE FROM player_settings WHERE player_uuid = ? RETURNING *;";
        Optional<PlayerSettingsDTO> before = getPlayerSettingsByUUID(uuid);
        if (before.isPresent()) return Optional.empty();
        try(var ps = db.getConnection().prepareStatement(deleteByPlayerUuid)) {
            ps.setString(1, uuid);
            try (var rs = ps.executeQuery()) {
                if (ps.executeUpdate() == 0) {
                    throw new RuntimeException("No player settings found to delete for UUID: " + uuid);
                }
                return before;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete player settings by UUID", e);
        }
    }
}
