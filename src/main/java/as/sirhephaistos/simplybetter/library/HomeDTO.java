package as.sirhephaistos.simplybetter.library;

// sb_homes
public record HomeDTO(
        Long id,
        String name,
        String createdAt,
        String ownerUuid,
        Long positionId
) {
}
