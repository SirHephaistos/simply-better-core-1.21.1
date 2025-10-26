package as.sirhephaistos.simplybetter.library;

// sb_rtp_settings
public record RtpSettingsDTO(
        Long worldId,
        int minRange,
        int maxRange,
        int cooldownSeconds
) {
}
