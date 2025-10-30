package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.TransactionDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_transactions.
 * Columns per TransactionDTO:
 * id BIGINT AUTO_INCREMENT PRIMARY KEY,
 * amount BIGINT NOT NULL,
 * date TEXT NOT NULL,                    // ISO-8601 recommended
 * account_player_uuid TEXT NOT NULL,     // FK -> sb_accounts.player_uuid
 * interact_player_uuid TEXT NULL         // optional counterparty
 * TODO: add indexes on (account_player_uuid, date) and interact_player_uuid.
 */
public final class TransactionsCrudManager {
    private final DatabaseManager db;

    public TransactionsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static TransactionDTO mapTransaction(ResultSet rs) throws SQLException {
        final long id = rs.getLong("t_id");
        final long amount = rs.getLong("t_amount");
        final String date = rs.getString("t_date");
        final String accountUuid = rs.getString("t_account_player_uuid");
        final String interactUuid = rs.getString("t_interact_player_uuid"); // may be null
        return new TransactionDTO(id, amount, date, accountUuid, interactUuid);
    }

    /**
     * Insert a new transaction row.
     */
    public TransactionDTO createTransaction(long amount,
                                            @NotNull String date,
                                            @NotNull String accountPlayerUuid,
                                            String interactPlayerUuid) {
        final String sql = """
                INSERT INTO sb_transactions (amount, date, account_player_uuid, interact_player_uuid)
                VALUES (?, ?, ?, ?)
                """;
        long id;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, amount);
            ps.setString(2, date);
            ps.setString(3, accountPlayerUuid);
            if (interactPlayerUuid == null) ps.setNull(4, Types.VARCHAR);
            else ps.setString(4, interactPlayerUuid);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("createTransaction: no generated key");
                id = keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createTransaction failed for account=" + accountPlayerUuid, e);
        }
        return getTransactionById(id)
                .orElseThrow(() -> new RuntimeException("createTransaction post-fetch missing id=" + id));
    }

    // -- Read

    /**
     * Insert a transaction and atomically adjust the target account balance.
     * Amount is applied to sb_accounts.balance (may be negative).
     * Sets sb_accounts.updated_at = date.
     */
    public TransactionDTO applyTransactionAndAdjustBalance(long amount,
                                                           @NotNull String date,
                                                           @NotNull String accountPlayerUuid,
                                                           String interactPlayerUuid) {
        final String insertTx = """
                INSERT INTO sb_transactions (amount, date, account_player_uuid, interact_player_uuid)
                VALUES (?, ?, ?, ?)
                """;
        final String selectBal = """
                SELECT balance
                FROM sb_accounts
                WHERE player_uuid = ?
                FOR UPDATE
                """;
        final String updateBal = """
                UPDATE sb_accounts
                SET balance = ?, updated_at = ?
                WHERE player_uuid = ?
                """;

        long newId;

        try (Connection c = db.getConnection()) {
            final boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                long current;
                try (PreparedStatement ps = c.prepareStatement(selectBal)) {
                    ps.setString(1, accountPlayerUuid);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next())
                            throw new RuntimeException("applyTransaction: account not found for player=" + accountPlayerUuid);
                        current = rs.getLong(1);
                    }
                }
                final long next = current + amount;
                try (PreparedStatement ps = c.prepareStatement(updateBal)) {
                    ps.setLong(1, next);
                    ps.setString(2, date);
                    ps.setString(3, accountPlayerUuid);
                    final int upd = ps.executeUpdate();
                    if (upd == 0)
                        throw new RuntimeException("applyTransaction: balance update affected 0 rows for player=" + accountPlayerUuid);
                }
                try (PreparedStatement ps = c.prepareStatement(insertTx, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, amount);
                    ps.setString(2, date);
                    ps.setString(3, accountPlayerUuid);
                    if (interactPlayerUuid == null) ps.setNull(4, Types.VARCHAR);
                    else ps.setString(4, interactPlayerUuid);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new RuntimeException("applyTransaction: no generated key");
                        newId = keys.getLong(1);
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                try {
                    c.rollback();
                } catch (SQLException ignore) {
                }
                throw ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException("applyTransaction failed", ex);
            } finally {
                try {
                    c.setAutoCommit(prevAuto);
                } catch (SQLException ignore) {
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("applyTransaction failed for account=" + accountPlayerUuid, e);
        }

        return getTransactionById(newId)
                .orElseThrow(() -> new RuntimeException("applyTransaction post-fetch missing id=" + newId));
    }

    /**
     * Get by id.
     */
    public Optional<TransactionDTO> getTransactionById(long id) {
        final String sql = """
                SELECT
                    t.id                   AS t_id,
                    t.amount               AS t_amount,
                    t.date                 AS t_date,
                    t.account_player_uuid  AS t_account_player_uuid,
                    t.interact_player_uuid AS t_interact_player_uuid
                FROM sb_transactions t
                WHERE t.id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapTransaction(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getTransactionById failed id=" + id, e);
        }
    }

    /**
     * List all transactions.
     */
    public List<TransactionDTO> getAllTransactions() {
        final String sql = """
                SELECT
                    t.id                   AS t_id,
                    t.amount               AS t_amount,
                    t.date                 AS t_date,
                    t.account_player_uuid  AS t_account_player_uuid,
                    t.interact_player_uuid AS t_interact_player_uuid
                FROM sb_transactions t
                ORDER BY t.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<TransactionDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapTransaction(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllTransactions failed", e);
        }
    }

    /**
     * List transactions for an account.
     */
    public List<TransactionDTO> getTransactionsByAccountUuid(@NotNull String accountPlayerUuid) {
        final String sql = """
                SELECT
                    t.id                   AS t_id,
                    t.amount               AS t_amount,
                    t.date                 AS t_date,
                    t.account_player_uuid  AS t_account_player_uuid,
                    t.interact_player_uuid AS t_interact_player_uuid
                FROM sb_transactions t
                WHERE t.account_player_uuid = ?
                ORDER BY t.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, accountPlayerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<TransactionDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapTransaction(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getTransactionsByAccountUuid failed for account=" + accountPlayerUuid, e);
        }
    }

    /**
     * List transactions by counterparty.
     */
    public List<TransactionDTO> getTransactionsByInteractUuid(@NotNull String interactPlayerUuid) {
        final String sql = """
                SELECT
                    t.id                   AS t_id,
                    t.amount               AS t_amount,
                    t.date                 AS t_date,
                    t.account_player_uuid  AS t_account_player_uuid,
                    t.interact_player_uuid AS t_interact_player_uuid
                FROM sb_transactions t
                WHERE t.interact_player_uuid = ?
                ORDER BY t.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, interactPlayerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                final List<TransactionDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapTransaction(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getTransactionsByInteractUuid failed for interact=" + interactPlayerUuid, e);
        }
    }

    // -- Update

    /**
     * List account transactions between [fromIso, toIso].
     */
    public List<TransactionDTO> getTransactionsForAccountBetween(@NotNull String accountPlayerUuid,
                                                                 @NotNull String fromIso,
                                                                 @NotNull String toIso) {
        final String sql = """
                SELECT
                    t.id                   AS t_id,
                    t.amount               AS t_amount,
                    t.date                 AS t_date,
                    t.account_player_uuid  AS t_account_player_uuid,
                    t.interact_player_uuid AS t_interact_player_uuid
                FROM sb_transactions t
                WHERE t.account_player_uuid = ?
                  AND t.date >= ?
                  AND t.date <= ?
                ORDER BY t.id DESC
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, accountPlayerUuid);
            ps.setString(2, fromIso);
            ps.setString(3, toIso);
            try (ResultSet rs = ps.executeQuery()) {
                final List<TransactionDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapTransaction(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getTransactionsForAccountBetween failed for account=" + accountPlayerUuid, e);
        }
    }

    // -- Delete

    /**
     * Update amount, date, and interact_player_uuid. Account UUID is immutable.
     */
    public TransactionDTO updateTransaction(long id, long amount, @NotNull String date, String interactPlayerUuid) {
        final String sql = """
                UPDATE sb_transactions
                SET amount = ?, date = ?, interact_player_uuid = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setString(2, date);
            if (interactPlayerUuid == null) ps.setNull(3, Types.VARCHAR);
            else ps.setString(3, interactPlayerUuid);
            ps.setLong(4, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("updateTransaction affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("updateTransaction failed id=" + id, e);
        }
        return getTransactionById(id)
                .orElseThrow(() -> new RuntimeException("updateTransaction post-fetch missing id=" + id));
    }

    // -- Mapper

    /**
     * Delete by id and return prior row if it existed.
     */
    public Optional<TransactionDTO> deleteTransactionById(long id) {
        final Optional<TransactionDTO> before = getTransactionById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_transactions
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteTransactionById failed id=" + id, e);
        }
    }
}
