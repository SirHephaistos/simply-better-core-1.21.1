package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.KitItemDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC CRUD manager for sb_kits_items.
 * Columns per KitItemDTO:
 * id BIGINT AUTO_INCREMENT PRIMARY KEY,
 * namespace TEXT NOT NULL,
 * item_name TEXT NOT NULL,
 * custom_name TEXT NULL,
 * custom_lore TEXT NULL,
 * custom_enchants TEXT NULL,   // serialized enchants, format defined by caller
 * quantity INTEGER NOT NULL,
 * kit_id BIGINT NOT NULL       // FK -> sb_kits(id)
 * TODO: add index on kit_id and possibly (kit_id, item_name).
 */
public final class KitItemsCrudManager {
    private final DatabaseManager db;

    public KitItemsCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
    }

    // -- Create

    private static KitItemDTO mapKitItem(ResultSet rs) throws SQLException {
        final long id = rs.getLong("ki_id");
        final String namespace = rs.getString("ki_namespace");
        final String itemName = rs.getString("ki_item_name");
        final String customName = rs.getString("ki_custom_name");       // may be null
        final String customLore = rs.getString("ki_custom_lore");       // may be null
        final String customEnchants = rs.getString("ki_custom_enchants");// may be null
        final int quantity = rs.getInt("ki_quantity");
        final long kitId = rs.getLong("ki_kit_id");
        return new KitItemDTO(id, namespace, itemName, customName, customLore, customEnchants, quantity, kitId);
    }

    // -- Read

    /**
     * Insert a new kit item.
     */
    public KitItemDTO createKitItem(@NotNull String namespace,
                                    @NotNull String itemName,
                                    String customName,
                                    String customLore,
                                    String customEnchants,
                                    int quantity,
                                    long kitId) {
        final String sql = """
                INSERT INTO sb_kits_items (namespace, item_name, custom_name, custom_lore, custom_enchants, quantity, kit_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        long id;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, namespace);
            ps.setString(2, itemName);
            if (customName == null) ps.setNull(3, Types.VARCHAR);
            else ps.setString(3, customName);
            if (customLore == null) ps.setNull(4, Types.VARCHAR);
            else ps.setString(4, customLore);
            if (customEnchants == null) ps.setNull(5, Types.VARCHAR);
            else ps.setString(5, customEnchants);
            ps.setInt(6, quantity);
            ps.setLong(7, kitId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new RuntimeException("createKitItem: no generated key");
                id = keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("createKitItem failed for kitId=" + kitId, e);
        }
        return getKitItemById(id).orElseThrow(() -> new RuntimeException("createKitItem post-fetch missing id=" + id));
    }

    /**
     * Get one by id.
     */
    public Optional<KitItemDTO> getKitItemById(long id) {
        final String sql = """
                SELECT
                    ki.id              AS ki_id,
                    ki.namespace       AS ki_namespace,
                    ki.item_name       AS ki_item_name,
                    ki.custom_name     AS ki_custom_name,
                    ki.custom_lore     AS ki_custom_lore,
                    ki.custom_enchants AS ki_custom_enchants,
                    ki.quantity        AS ki_quantity,
                    ki.kit_id          AS ki_kit_id
                FROM sb_kits_items ki
                WHERE ki.id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapKitItem(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("getKitItemById failed id=" + id, e);
        }
    }

    /**
     * List all kit items.
     */
    public List<KitItemDTO> getAllKitItems() {
        final String sql = """
                SELECT
                    ki.id              AS ki_id,
                    ki.namespace       AS ki_namespace,
                    ki.item_name       AS ki_item_name,
                    ki.custom_name     AS ki_custom_name,
                    ki.custom_lore     AS ki_custom_lore,
                    ki.custom_enchants AS ki_custom_enchants,
                    ki.quantity        AS ki_quantity,
                    ki.kit_id          AS ki_kit_id
                FROM sb_kits_items ki
                ORDER BY ki.kit_id, ki.id
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            final List<KitItemDTO> out = new ArrayList<>();
            while (rs.next()) out.add(mapKitItem(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("getAllKitItems failed", e);
        }
    }

    /**
     * List items by kit id.
     */
    public List<KitItemDTO> getKitItemsByKitId(long kitId) {
        final String sql = """
                SELECT
                    ki.id              AS ki_id,
                    ki.namespace       AS ki_namespace,
                    ki.item_name       AS ki_item_name,
                    ki.custom_name     AS ki_custom_name,
                    ki.custom_lore     AS ki_custom_lore,
                    ki.custom_enchants AS ki_custom_enchants,
                    ki.quantity        AS ki_quantity,
                    ki.kit_id          AS ki_kit_id
                FROM sb_kits_items ki
                WHERE ki.kit_id = ?
                ORDER BY ki.id
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, kitId);
            try (ResultSet rs = ps.executeQuery()) {
                final List<KitItemDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapKitItem(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getKitItemsByKitId failed for kitId=" + kitId, e);
        }
    }

    // -- Update

    /**
     * Optional convenience: find items by kit and item_name.
     */
    public List<KitItemDTO> getKitItemsByKitAndName(long kitId, @NotNull String itemName) {
        final String sql = """
                SELECT
                    ki.id              AS ki_id,
                    ki.namespace       AS ki_namespace,
                    ki.item_name       AS ki_item_name,
                    ki.custom_name     AS ki_custom_name,
                    ki.custom_lore     AS ki_custom_lore,
                    ki.custom_enchants AS ki_custom_enchants,
                    ki.quantity        AS ki_quantity,
                    ki.kit_id          AS ki_kit_id
                FROM sb_kits_items ki
                WHERE ki.kit_id = ? AND ki.item_name = ?
                ORDER BY ki.id
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, kitId);
            ps.setString(2, itemName);
            try (ResultSet rs = ps.executeQuery()) {
                final List<KitItemDTO> out = new ArrayList<>();
                while (rs.next()) out.add(mapKitItem(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getKitItemsByKitAndName failed for kitId=" + kitId, e);
        }
    }

    /**
     * Update all fields.
     */
    public KitItemDTO updateKitItem(long id,
                                    @NotNull String namespace,
                                    @NotNull String itemName,
                                    String customName,
                                    String customLore,
                                    String customEnchants,
                                    int quantity,
                                    long kitId) {
        final String sql = """
                UPDATE sb_kits_items
                SET namespace = ?, item_name = ?, custom_name = ?, custom_lore = ?, custom_enchants = ?, quantity = ?, kit_id = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, namespace);
            ps.setString(2, itemName);
            if (customName == null) ps.setNull(3, Types.VARCHAR);
            else ps.setString(3, customName);
            if (customLore == null) ps.setNull(4, Types.VARCHAR);
            else ps.setString(4, customLore);
            if (customEnchants == null) ps.setNull(5, Types.VARCHAR);
            else ps.setString(5, customEnchants);
            ps.setInt(6, quantity);
            ps.setLong(7, kitId);
            ps.setLong(8, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("updateKitItem affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("updateKitItem failed id=" + id, e);
        }
        return getKitItemById(id).orElseThrow(() -> new RuntimeException("updateKitItem post-fetch missing id=" + id));
    }

    // -- Delete

    /**
     * Update quantity only.
     */
    public KitItemDTO setKitItemQuantity(long id, int quantity) {
        final String sql = """
                UPDATE sb_kits_items
                SET quantity = ?
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setLong(2, id);
            final int upd = ps.executeUpdate();
            if (upd == 0) throw new RuntimeException("setKitItemQuantity affected 0 rows id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException("setKitItemQuantity failed id=" + id, e);
        }
        return getKitItemById(id).orElseThrow(() -> new RuntimeException("setKitItemQuantity post-fetch missing id=" + id));
    }

    /**
     * Delete by id and return the prior row if it existed.
     */
    public Optional<KitItemDTO> deleteKitItemById(long id) {
        final Optional<KitItemDTO> before = getKitItemById(id);
        if (before.isEmpty()) return Optional.empty();

        final String sql = """
                DELETE FROM sb_kits_items
                WHERE id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            return before;
        } catch (SQLException e) {
            throw new RuntimeException("deleteKitItemById failed id=" + id, e);
        }
    }

    // -- Mapper

    /**
     * Delete all items for a kit. Returns the count.
     */
    public int deleteKitItemsByKitId(long kitId) {
        final String sql = """
                DELETE FROM sb_kits_items
                WHERE kit_id = ?
                """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, kitId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("deleteKitItemsByKitId failed for kitId=" + kitId, e);
        }
    }
}
