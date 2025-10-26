package as.sirhephaistos.simplybetter.library;

// sb_kits_items
public record KitItemDTO(
        Long id,
        String namespace,
        String itemName,
        String customName,
        String customLore,
        String customEnchants,
        int quantity,
        Long kitId
) {
}
