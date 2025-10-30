package as.sirhephaistos.simplybetter.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// sb_audit_logs
public record AuditLogDTO(
        @NotNull Long id,
        @NotNull String tableName,
        @Nullable String initiator,
        @Nullable String contextJson,
        @NotNull String at
) {
}
