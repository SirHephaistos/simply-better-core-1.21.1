package as.sirhephaistos.simplybetter.library;

import org.jetbrains.annotations.NotNull;

// sb_homes
public record HomeDTO(
        @NotNull Long id,
        @NotNull String name,
        @NotNull String createdAt,
        @NotNull String ownerUuid,
        @NotNull Long positionId
) {
}
