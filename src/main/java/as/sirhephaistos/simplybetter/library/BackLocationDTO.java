package as.sirhephaistos.simplybetter.library;

// sb_back_locations
public record BackLocationDTO(
        String playerUuid,
        String updatedAt,
        Long previousPositionId,
        Long currentPositionId
) {
}
