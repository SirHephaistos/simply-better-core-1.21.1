package as.sirhephaistos.simplybetter.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * DTOs generated from schema.sql (excluding WarpDTO and PositionDTO). :contentReference[oaicite:1]{index=1}
 * Notes:
 * - TEXT dates kept as String.
 * - INTEGER flags mapped to boolean.
 * - SERIAL ids mapped to Long.
 */

// sb_players
public record PlayerDTO(
        @NotNull String uuid,
        @NotNull String name,
        @NotNull String firstSeen,
        @NotNull String lastSeen,
        long playtimeSeconds,
        boolean canBeIgnored,
        @Nullable String nickname,
        @Nullable Long lastSeenPositionId
) {
}
