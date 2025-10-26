package as.sirhephaistos.simplybetter.library;

// sb_transactions
public record TransactionDTO(
        Long id,
        long amount,
        String date,
        String accountPlayerUuid,
        String interactPlayerUuid
) {
}
