package as.sirhephaistos.simplybetter.library;

// sb_audit_logs
public record AuditLogDTO(
        Long id,
        String tableName,
        String initiator,
        String contextJson,
        String at
) {
}
