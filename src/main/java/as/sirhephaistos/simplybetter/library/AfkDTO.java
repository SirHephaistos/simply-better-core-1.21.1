package as.sirhephaistos.simplybetter.library;

// sb_afks
public record AfkDTO(
        String playerUuid,
        long sinceSeconds,
        String message
) {
}
