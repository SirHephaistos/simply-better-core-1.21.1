package as.sirhephaistos.simplybetter.core.db;

import org.jetbrains.annotations.NotNull;

public final class UserLogsCrudManager {
    private final DatabaseManager db;

    public UserLogsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }
}
