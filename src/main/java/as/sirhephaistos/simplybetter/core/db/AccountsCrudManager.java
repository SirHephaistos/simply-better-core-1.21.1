package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.AccountDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <h1><img src="https://docs.godsmg.com/~gitbook/image?url=https%3A%2F%2F602320278-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_hKBWF%252Ficon%252FF3ga5TrIrIMXtWecHo3z%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252017_44_38.png%3Falt%3Dmedia%26token%3D8c3f45e4-ed6f-47ab-a4ab-474d24fa3bb3&width=32&dpr=1&quality=100&sign=2c456f01&sv=2"></img>
 * &nbsp;CRUD manager for {@link AccountDTO}
 * <img src="https://docs-sbs.godsmg.com/~gitbook/image?url=https%3A%2F%2F655127117-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_ofAiW%252Ficon%252F9SRBPTo3OKBsw5DvBwL3%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252000_07_28.png%3Falt%3Dmedia%26token%3D396dda36-5693-4638-b53e-59bf0770f309&width=32&dpr=1&quality=100&sign=55c114e6&sv=2"></img> </h1>
 * <h2>Create Methods</h2>
 * <ul>
 *     <li>{@link #createAccount}:</br>
 *         Create a new account for the given player UUID. And returns an {@link AccountDTO}.
 *     </li>
 * </ul>
 * <h2>Read Methods</h2>
 * <ul>
 *     <li>{@link #getAccountByPlayerUuid}:</br>
 *         Get account by Player UUID. And return an {@link Optional} containing {@link AccountDTO} if found, empty otherwise.</li>
 *     <li>{@link #getAllAccounts}:</br>
 *         Return a list of all accounts in the database. And  returns a {@link List} of {@link AccountDTO}.</li>
 *     <li>{@link #getAllAccountsPaged}:</br>
 *         Return a paged list of accounts in the database. And  returns a {@link List} of {@link AccountDTO}.</li>
 * </ul>
 * <h2>Update Methods</h2>
 * <ul>
 *     <li>{@link #updateAccount}:</br>
 *         Update account balance and updated_at by player UUID. And  returns the updated {@link AccountDTO}.</li>
 * </ul>
 * <h2>Delete Methods</h2>
 * <ul>
 *     <li>{@link #deleteAccountByPlayerUuid}:</br>
 *     Delete account by Player UUID.</li>
 * </ul>
 *
 *<h3>General Information</h3>
 * @codeBaseStatus Complete
 * @testingStatus AwaitingJUnitTests
 * @author Sirhephaistos
 * @version 1.0
 */
@SuppressWarnings("ClassCanBeRecord")
public final class AccountsCrudManager {
    private final DatabaseManager db;

    public AccountsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    /**
     * Private helper to get a mounted account from a ResultSet.
     * @param rs {@link ResultSet} positioned at the desired row.
     * @return Mapped {@link AccountDTO}.
     * @throws SQLException on SQL errors comming from jdbc.
     * @throws IllegalArgumentException if rs is null.
     * @throws IllegalStateException if any non-nullable column is null.
     */
    private static AccountDTO mapAccount(ResultSet rs) throws SQLException{
        if (rs == null) throw new IllegalArgumentException("mapAccount: rs is null");
        if (rs.getString("a_player_uuid") == null)
            throw new IllegalStateException("mapAccount: player_uuid is null");
        if (rs.getString("a_balance") == null)
            throw new IllegalStateException("mapAccount: balance is null");
        if (rs.getString("a_updated_at") == null)
            throw new IllegalStateException("mapAccount: updated_at is null");
        @NotNull final String playerUuid = rs.getString("a_player_uuid");
        final long balance = rs.getLong("a_balance");
        @NotNull final String updatedAt = rs.getString("a_updated_at");
        return new AccountDTO(playerUuid, balance, updatedAt);
    }

    /**
     * Create a new account for the given player UUID
     * @param playerUuid Owner {@code playerUuid}.
     * @param balance Initial balance.
     * @param updatedAt Initial updated_at as string, this allows for example to use the time coming from the source
     * @return Created AccountDTO.
     * @throws IllegalArgumentException if an account already exists for the given player UUID.
     * @throws RuntimeException on SQL errors or if post-fetch fails.
     */
    public AccountDTO createAccount(@NotNull String playerUuid,
                                    long balance,
                                    @NotNull String updatedAt) {
        if (getAccountByPlayerUuid(playerUuid).isPresent()) {
            throw new IllegalArgumentException("createAccount: account already exists for playerUuid=" + playerUuid);
        }
        final String sql = """
                INSERT INTO sb_accounts (player_uuid, balance, updated_at)
                VALUES (?, ?, ?)
                """;
        String id;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, playerUuid);
            ps.setLong(2, balance);
            ps.setString(3, updatedAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("createAccount: no generated key");
                id = keys.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createAccount failed for player=" + playerUuid, e);
        }
        return getAccountByPlayerUuid(id).orElseThrow(() -> new RuntimeException("createAccount post-fetch missing playerUuid=" + id));
    }
    // -- Read

    /**
     * Get account by Player UUID.
     * @param playerUuid Owner's UUID.
     * @return Optional containing AccountDTO if found, empty otherwise.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<AccountDTO> getAccountByPlayerUuid(@NotNull String playerUuid) {
        final String sql = """
                SELECT
                    a.playerUuid          AS a_id,
                    a.player_uuid AS a_player_uuid,
                    a.balance     AS a_balance,
                    a.created_at  AS a_created_at,
                    a.updated_at  AS a_updated_at
                FROM sb_accounts a
                WHERE a.player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapAccount(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getAccountByPlayerUuid failed for player=" + playerUuid, e);
        }
    }

    /**
     * Return a list of all accounts in the database.
     * @return List of AccountDTO.
     * @throws RuntimeException on SQL errors.
     */
    public List<AccountDTO> getAllAccounts() {
        final String sql = """
                SELECT
                    a.player_uuid AS a_player_uuid,
                    a.balance     AS a_balance,
                    a.updated_at  AS a_updated_at
                FROM sb_accounts a
                ORDER BY a.playerUuid
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<AccountDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapAccount(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllAccounts failed", e);
        }
    }

    /**
     * Return a paged list of accounts in the database.
     * @param limit Maximum number of accounts to return.
     * @param offset Number of accounts to skip.
     * @return List of AccountDTO.
     * @throws RuntimeException on SQL errors.
     */
    public List<AccountDTO> getAllAccountsPaged(int limit, int offset)
    {
        final  String sql = """
                SELECT
                    a.player_uuid AS a_player_uuid,
                    a.balance     AS a_balance,
                    a.updated_at  AS a_updated_at
                FROM sb_accounts a
                ORDER BY a.playerUuid
                LIMIT ? OFFSET ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                final List<AccountDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapAccount(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getAllAccountsPaged failed", e);
        }
    }

    // -- Update

    /**
     * Update account balance and updated_at by player UUID.
     * @param playerUuid Owner's UUID.
     * @param balance New balance.
     * @param updatedAt New updated_at string. This allows for example to use the time coming from the source
     * @return Updated AccountDTO. It is guaranteed to be present because we are fetching after updating.
     * @throws RuntimeException on SQL errors or if no rows were affected.
     */
    public AccountDTO updateAccount(@NotNull String playerUuid, long balance, @NotNull String updatedAt) {
        final String sql = """
                UPDATE sb_accounts
                SET balance = ?, updated_at = ?
                WHERE playerUuid = ?
                """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, balance);
            ps.setString(2, updatedAt);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("updateAccount affected 0 rows players uuid=" + playerUuid);
        } catch (SQLException e) {
            throw new RuntimeException("updateAccount failed playerUuid=" + playerUuid, e);
        }
        return getAccountByPlayerUuid(playerUuid).orElseThrow(() -> new RuntimeException("updateAccount post-fetch missing playerUuid=" + playerUuid));
    }

    // -- Delete
    /**
     * Delete account by Player UUID.
     * @param playerUuid Owner's UUID.
     * @throws IllegalArgumentException if account not found.
     * @throws RuntimeException on SQL errors or if no rows were affected.
     */
    public void deleteAccountByPlayerUuid(@NotNull String playerUuid) {
        if (getAccountByPlayerUuid(playerUuid).isEmpty()) {
            throw new IllegalArgumentException("deleteAccountByPlayerUuid: account not found for playerUuid=" + playerUuid);
        }
        final String sql = """
                DELETE FROM sb_accounts
                WHERE player_uuid = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.executeUpdate();
            if (getAccountByPlayerUuid(playerUuid).isPresent()) {
                throw new RuntimeException("deleteAccountByPlayerUuid failed to delete playerUuid=" + playerUuid);
            }
        } catch (SQLException e) {
            throw new RuntimeException("deleteAccountByPlayerUuid failed for player=" + playerUuid, e);
        }
    }
}
