package as.sirhephaistos.simplybetter.core.db;

import org.jetbrains.annotations.NotNull;

public final class ChatLogsCrudManager {
    private final DatabaseManager db;

    public ChatLogsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }
}
