package as.sirhephaistos.simplybetter.library;

// sb_mails
public record MailDTO(
        Long id,
        boolean isRead,
        String subject,
        String content,
        String readAt,
        String sentAt,
        String expiresAt,
        String senderPlayerUuid,
        String targetPlayerUuid
) {
}
