package as.sirhephaistos.simplybetter.library;

// sb_jails
public record JailDTO(
        Long id,
        boolean canBeVisited,
        Long centerPositionId,
        Long visitEntryPositionId
) {
}
