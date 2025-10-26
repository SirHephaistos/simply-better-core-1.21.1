package as.sirhephaistos.simplybetter.library;

// sb_bans
public record BanDTO(
        Long id,
        String createdAt,
        String expiresAt,
        String reason,
        String playerUuid,
        String bannedByUuid
) {
}
