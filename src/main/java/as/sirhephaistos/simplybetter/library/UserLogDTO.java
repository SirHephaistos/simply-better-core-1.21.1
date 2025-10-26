package as.sirhephaistos.simplybetter.library;

// sb_user_logs
public record UserLogDTO(
        Long id,
        String at,
        String description,
        String playerUuid,
        Long playerPositionId,
        Long interactPositionId
) {
}
