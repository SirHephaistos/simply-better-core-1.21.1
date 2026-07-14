package as.sirhephaistos.simplybetter.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record WarpDTO(
        long id,
        @NotNull String name,
        @NotNull PositionDTO position,
        @Nullable String createdBy,
        @NotNull String createdAt) {
}
