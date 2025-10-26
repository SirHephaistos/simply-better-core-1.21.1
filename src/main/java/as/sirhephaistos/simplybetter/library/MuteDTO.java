package as.sirhephaistos.simplybetter.library;

// sb_mutes
public record MuteDTO(
        Long id,
        String createdAt,
        String expiresAt,
        String reason,
        String playerUuid,
        String mutedByUuid
) {
}
