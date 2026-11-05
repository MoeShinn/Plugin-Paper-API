package dev.autokit;

import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tinh gia 1 ItemStack:
 *  - gia item goc (buy; neu chi co sell thi sell x he so)
 *  - + gia sach enchant cho tung enchant (gia dung cap, neu khong co thi quy doi theo cap gan nhat x 2^(chenh lech))
 *  - + gia moi item long ben trong (shulker, ruong, bundle)
 *  - x so luong
 * Item/enchant khong co gia -> 0 (mien phi).
 */
public final class ItemPricer {

    private record Cached(double price, long expiresAt) {
    }

    private final AutoKitPlugin plugin;
    private final Map<ItemStack, Cached> cache = new HashMap<>();

    public ItemPricer(AutoKitPlugin plugin) {
        this.plugin = plugin;
    }

    public void clearCache() {
        cache.clear();
    }

    public double price(ItemStack item) {
        return price(item, 0, null);
    }

    /** trace != null: ghi lai chi tiet cach tinh (dung cho /autokit debug). */
    public double price(ItemStack item, List<String> trace) {
        return price(item, 0, trace);
    }

    private double price(ItemStack item, int depth, List<String> trace) {
        if (ItemUtil.isAir(item)) return 0;
        String pad = "  ".repeat(depth);
        double unit = 0;
        ItemMeta meta = item.getItemMeta();

        if (meta instanceof EnchantmentStorageMeta storage && storage.hasStoredEnchants()) {
            double direct = lookup(ItemUtil.single(item));
            if (direct > 0) {
                unit = direct;
                t(trace, pad + "Sach enchant (khop truc tiep trong shop): " + direct);
            } else {
                for (Map.Entry<Enchantment, Integer> e : storage.getStoredEnchants().entrySet()) {
                    unit += enchantPrice(e.getKey(), e.getValue(), pad, trace);
                }
            }
        } else {
            unit += baseUnit(item, pad, trace);
            for (Map.Entry<Enchantment, Integer> e : item.getEnchantments().entrySet()) {
                unit += enchantPrice(e.getKey(), e.getValue(), pad, trace);
            }
        }

        if (depth < ItemUtil.MAX_DEPTH) {
            for (ItemStack inner : ItemUtil.nestedItems(item)) {
                t(trace, pad + "  [ben trong " + item.getType() + "]");
                unit += price(inner, depth + 1, trace);
            }
        }

        double total = unit * item.getAmount();
        t(trace, pad + "=> " + item.getAmount() + "x " + item.getType() + " = " + total);
        return total;
    }

    private double baseUnit(ItemStack item, String pad, List<String> trace) {
        ItemStack plain = new ItemStack(item.getType());
        ItemStack stripped = ItemUtil.stripForLookup(item);

        double p = lookup(stripped);
        if (p > 0) {
            t(trace, pad + item.getType() + ": " + p);
            return p;
        }
        if (!stripped.isSimilar(plain) && plainFallbackAllowed(stripped.getItemMeta())) {
            p = lookup(plain);
            if (p > 0) {
                t(trace, pad + item.getType() + " (bo qua ten/lore/meta): " + p);
                return p;
            }
        }
        t(trace, pad + item.getType() + ": khong co gia -> mien phi");
        return 0;
    }

    private double enchantPrice(Enchantment ench, int level, String pad, List<String> trace) {
        String name = ench.getKey().asString() + " " + level;

        double exact = lookup(book(ench, level));
        if (exact > 0) {
            t(trace, pad + "  + " + name + ": " + exact);
            return exact;
        }

        int max = Math.max(1, ench.getMaxLevel());
        int bestLevel = -1;
        double bestPrice = -1;
        for (int k = 1; k <= max; k++) {
            if (k == level) continue;
            double p = lookup(book(ench, k));
            if (p <= 0) continue;
            if (bestLevel < 0 || Math.abs(k - level) < Math.abs(bestLevel - level)) {
                bestLevel = k;
                bestPrice = p;
            }
        }
        if (bestLevel < 0) {
            t(trace, pad + "  + " + name + ": shop khong co sach -> mien phi");
            return 0;
        }
        double value = bestPrice * Math.pow(2, level - bestLevel);
        t(trace, pad + "  + " + name + ": " + bestPrice + " (cap " + bestLevel + ") x 2^" + (level - bestLevel) + " = " + value);
        return value;
    }

    private static ItemStack book(Enchantment ench, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(ench, level, true);
        book.setItemMeta(meta);
        return book;
    }

    private static boolean plainFallbackAllowed(ItemMeta meta) {
        if (meta == null) return true;
        if (meta instanceof PotionMeta || meta instanceof SuspiciousStewMeta
                || meta instanceof SkullMeta || meta instanceof FireworkMeta) {
            return false;
        }
        if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof CreatureSpawner) {
            return false;
        }
        return true;
    }

    /** Gia don vi (so luong 1) cua dung item nay trong shop, -1 neu khong co. Co cache. */
    private double lookup(ItemStack key) {
        long now = System.currentTimeMillis();
        long ttl = plugin.settings().priceCacheMs;
        if (ttl > 0) {
            Cached cached = cache.get(key);
            if (cached != null && cached.expiresAt() > now) return cached.price();
        }
        double value = fetch(key);
        if (ttl > 0) {
            if (cache.size() > 4096) cache.clear();
            cache.put(key.clone(), new Cached(value, now + ttl));
        }
        return value;
    }

    private double fetch(ItemStack key) {
        ShopPriceProvider shop = plugin.shop();
        double buy = shop.buyPrice(key);
        if (buy > 0) return buy;
        double sell = shop.sellPrice(key);
        if (sell > 0) return sell * plugin.settings().sellMultiplier;
        return -1;
    }

    private static void t(List<String> trace, String line) {
        if (trace != null) trace.add(line);
    }
}
