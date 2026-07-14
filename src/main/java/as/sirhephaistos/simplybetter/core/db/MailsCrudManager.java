package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.MailDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MailsCrudManager {
    private final DatabaseManager db;

    public MailsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }
}
