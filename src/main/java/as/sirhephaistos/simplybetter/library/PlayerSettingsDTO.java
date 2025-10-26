package as.sirhephaistos.simplybetter.library;

// sb_players_settings
public record PlayerSettingsDTO(
        String playerUuid,
        boolean tpaBlocked,
        boolean msgBlocked,
        boolean autoTpAccept,
        boolean payBlocked,
        boolean payConfirm,
        boolean clearinvConfirm,
        boolean vanished
) {
}
