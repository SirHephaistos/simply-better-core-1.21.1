package as.sirhephaistos.simplybetter.core.db;

import org.jetbrains.annotations.NotNull;

public final class TransactionsCrudManager {
    private final DatabaseManager db;

    public TransactionsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }
}
