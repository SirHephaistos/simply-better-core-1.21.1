package as.sirhephaistos.simplybetter.library;

// sb_jails_sanctions
public record JailSanctionDTO(
        Long id,
        String createdAt,
        String expiresAt,
        String reason,
        String playerUuid,
        String bannedByUuid,
        Long jailId
) {
}
