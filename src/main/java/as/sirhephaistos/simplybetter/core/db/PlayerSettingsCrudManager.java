package as.sirhephaistos.simplybetter.core.db;

import org.jetbrains.annotations.NotNull;

public final class PlayerSettingsCrudManager {
    private final DatabaseManager db;

    public PlayerSettingsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }
}
