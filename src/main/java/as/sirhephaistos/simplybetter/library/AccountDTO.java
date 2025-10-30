package as.sirhephaistos.simplybetter.library;

import org.jetbrains.annotations.NotNull;

// sb_accounts
public record AccountDTO(
        @NotNull String playerUuid,
        long balance,
        @NotNull String updatedAt // nullable
) {}
