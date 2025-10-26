package as.sirhephaistos.simplybetter.library;

// sb_private_chat_logs
public record PrivateChatLogDTO(
        Long id,
        String at,
        String content,
        String senderPlayerUuid,
        String receiverPlayerUuid
) {
}
