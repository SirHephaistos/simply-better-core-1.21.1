package as.sirhephaistos.simplybetter.library;

import org.jetbrains.annotations.NotNull;

// sb_back_locations
public record BackLocationDTO(
        @NotNull String playerUuid,
        @NotNull String updatedAt,
        @NotNull Long previousPositionId,
        @NotNull Long currentPositionId
) {
}
