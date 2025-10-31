package as.sirhephaistos.simplybetter.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// sb_worlds
public record WorldDTO(
        @NotNull Long id,
        @NotNull String dimensionId,
        @Nullable Long centerPositionId
) {}

