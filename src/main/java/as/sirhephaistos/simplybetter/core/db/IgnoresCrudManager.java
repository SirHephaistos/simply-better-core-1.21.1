package as.sirhephaistos.simplybetter.core.db;

import org.jetbrains.annotations.NotNull;

public final class IgnoresCrudManager {
    private final DatabaseManager db;

    public IgnoresCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }
}
