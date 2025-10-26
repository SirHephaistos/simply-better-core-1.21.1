package as.sirhephaistos.simplybetter.library;

/**
 * DTOs generated from schema.sql (excluding WarpDTO and PositionDTO). :contentReference[oaicite:1]{index=1}
 * Notes:
 * - TEXT dates kept as String.
 * - INTEGER flags mapped to boolean.
 * - AUTOINCREMENT ids mapped to Long.
 */

// sb_players
public record PlayerDTO(
        String uuid,
        String name,
        String firstSeen,
        String lastSeen,
        long playtimeSeconds,
        boolean canBeIgnored,
        String nickname,
        Long lastSeenPositionId
) {
}
