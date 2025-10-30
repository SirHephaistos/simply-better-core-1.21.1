package as.sirhephaistos.simplybetter.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// sb_afks
public record AfkDTO(
        @NotNull String playerUuid,
        @NotNull String since,
        @Nullable String message
) {
}
