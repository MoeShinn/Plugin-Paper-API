package dev.autokit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ItemUtil {

    /** Do sau toi da khi duyet do long nhau (shulker, bundle). */
    public static final int MAX_DEPTH = 4;

    private ItemUtil() {
    }

    public record BoxRef(UUID owner, String kitId) {
    }

    public static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    // ---------------------------------------------------------------- hop nhac

    public static ItemStack createBox(AutoKitPlugin plugin, UUID owner, String kitId) {
        ItemStack item = new ItemStack(plugin.settings().boxMaterial);
        ItemMeta meta = item.getItemMeta();

        String ownerName = Bukkit.getOfflinePlayer(owner).getName();
        if (ownerName == null) ownerName = owner.toString().substring(0, 8);
        TagResolver[] r = {
                Placeholder.unparsed("id", kitId),
                Placeholder.unparsed("owner", ownerName)
        };

        meta.displayName(noItalic(plugin.messages().get("box.name", r)));
        List<Component> lore = new ArrayList<>();
        for (Component c : plugin.messages().list("box.lore", r)) lore.add(noItalic(c));
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(plugin.keys().box(), PersistentDataType.BYTE, (byte) 1);
        pdc.set(plugin.keys().owner(), PersistentDataType.STRING, owner.toString());
        pdc.set(plugin.keys().kit(), PersistentDataType.STRING, kitId);
        item.setItemMeta(meta);
        return item;
    }

    public static BoxRef readBox(AutoKitPlugin plugin, ItemStack item) {
        if (isAir(item) || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(plugin.keys().box(), PersistentDataType.BYTE)) return null;
        String owner = pdc.get(plugin.keys().owner(), PersistentDataType.STRING);
        String kit = pdc.get(plugin.keys().kit(), PersistentDataType.STRING);
        if (owner == null || kit == null) return null;
        try {
            return new BoxRef(UUID.fromString(owner), kit);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    // ------------------------------------------------------------- do ben / long

    /** Ban sao item voi do ben ve max (ke ca item nam trong shulker/bundle). */
    public static ItemStack normalizeDurability(ItemStack original, int depth) {
        ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        boolean changed = false;

        if (meta instanceof Damageable damageable && damageable.hasDamage()) {
            damageable.setDamage(0);
            changed = true;
        }

        if (depth < MAX_DEPTH) {
            if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState()) {
                BlockState state = bsm.getBlockState();
                if (state instanceof Container container) {
                    Inventory inv = container.getSnapshotInventory();
                    for (int i = 0; i < inv.getSize(); i++) {
                        ItemStack inner = inv.getItem(i);
                        if (!isAir(inner)) inv.setItem(i, normalizeDurability(inner, depth + 1));
                    }
                    bsm.setBlockState(state);
                    changed = true;
                }
            }
            if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
                List<ItemStack> fixed = new ArrayList<>();
                for (ItemStack inner : bundle.getItems()) fixed.add(normalizeDurability(inner, depth + 1));
                bundle.setItems(fixed);
                changed = true;
            }
        }

        if (changed) item.setItemMeta(meta);
        return item;
    }

    /** Cac item nam ben trong item nay (shulker, ruong, bundle...). */
    public static List<ItemStack> nestedItems(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return List.of();
        List<ItemStack> out = new ArrayList<>();

        if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState()) {
            if (bsm.getBlockState() instanceof Container container) {
                for (ItemStack inner : container.getSnapshotInventory().getContents()) {
                    if (!isAir(inner)) out.add(inner);
                }
            }
        }
        if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
            for (ItemStack inner : bundle.getItems()) {
                if (!isAir(inner)) out.add(inner);
            }
        }
        return out;
    }

    public static ItemStack single(ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        return copy;
    }

    /** Item de tra gia: so luong 1, do ben full, bo enchant (enchant tinh rieng bang sach). */
    public static ItemStack stripForLookup(ItemStack item) {
        ItemStack copy = single(item);
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return copy;
        if (meta instanceof Damageable damageable) damageable.setDamage(0);
        for (Enchantment e : new ArrayList<>(meta.getEnchants().keySet())) meta.removeEnchant(e);
        copy.setItemMeta(meta);
        return copy;
    }
}
