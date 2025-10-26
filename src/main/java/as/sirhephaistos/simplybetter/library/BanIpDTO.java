package as.sirhephaistos.simplybetter.library;

// sb_bans_ip
public record BanIpDTO(
        Long id,
        String createdAt,
        String expiresAt,
        String reason,
        String ipAddress,
        String playerUuid,
        String bannedByUuid
) {
}
