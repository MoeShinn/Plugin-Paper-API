package dev.autokit;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Doc gia tu EconomyShopGUI qua reflection (khong can dependency luc build):
 * EconomyShopGUIHook.getBuyPrice(OfflinePlayer, ItemStack) / getSellPrice(...) -> Optional of BuyPrice/SellPrice,
 * roi BuyPrice#getPrices() -> Map of EcoType, Double.
 *
 * Dung 1 OfflinePlayer "gia" de ESG KHONG kiem tra permission / requirement cua nguoi click
 * (neu khong, nguoi thieu quyen se bi coi la item mien phi).
 */
public final class ShopPriceProvider {

    private final AutoKitPlugin plugin;
    private final OfflinePlayer pricePlayer =
            Bukkit.getOfflinePlayer(UUID.nameUUIDFromBytes("AutoKit-Pricing".getBytes(StandardCharsets.UTF_8)));

    private final Map<Class<?>, Method> pricesMethods = new HashMap<>();
    private final Set<String> warned = new HashSet<>();

    private Method buyMethod;
    private Method sellMethod;
    private boolean ready;

    public ShopPriceProvider(AutoKitPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isReady() {
        return ready;
    }

    public void init() {
        ready = false;
        Plugin esg = Bukkit.getPluginManager().getPlugin("EconomyShopGUI");
        if (esg == null) esg = Bukkit.getPluginManager().getPlugin("EconomyShopGUI-Premium");
        if (esg == null || !esg.isEnabled()) {
            plugin.getLogger().severe("Khong tim thay EconomyShopGUI. AutoKit se tu choi tinh gia (an toan, khong cho kit mien phi).");
            return;
        }
        try {
            Class<?> hook = Class.forName("me.gypopo.economyshopgui.api.EconomyShopGUIHook", true, esg.getClass().getClassLoader());
            buyMethod = hook.getMethod("getBuyPrice", OfflinePlayer.class, ItemStack.class);
            sellMethod = hook.getMethod("getSellPrice", OfflinePlayer.class, ItemStack.class);
            ready = true;
            plugin.getLogger().info("Da ket noi " + esg.getName() + " " + esg.getPluginMeta().getVersion());
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().severe("Khong tim thay ESG API getBuyPrice/getSellPrice. Can EconomyShopGUI >= 6.3 (Premium >= 5.6). Loi: " + e);
        }
    }

    /** Gia mua cua 1 item, -1 neu khong ban/khong co gia. Nem IllegalStateException neu API loi. */
    public double buyPrice(ItemStack item) {
        return extract(invoke(buyMethod, item));
    }

    /** Gia ban lai cua 1 item, -1 neu khong co gia. */
    public double sellPrice(ItemStack item) {
        return extract(invoke(sellMethod, item));
    }

    private Object invoke(Method method, ItemStack item) {
        if (!ready || method == null) throw new IllegalStateException("EconomyShopGUI chua san sang");
        try {
            return method.invoke(null, pricePlayer, item.clone());
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("ESG API loi: " + e.getCause(), e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private double extract(Object result) {
        if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return -1;
        Object priceObj = optional.get();

        Object pricesObj;
        try {
            Method getPrices = pricesMethods.computeIfAbsent(priceObj.getClass(), c -> {
                try {
                    Method m = c.getMethod("getPrices");
                    m.setAccessible(true);
                    return m;
                } catch (ReflectiveOperationException | RuntimeException e) {
                    throw new IllegalStateException("Khong co getPrices() trong " + c.getName(), e);
                }
            });
            pricesObj = getPrices.invoke(priceObj);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        if (!(pricesObj instanceof Map<?, ?> prices)) return -1;

        for (Map.Entry<?, ?> entry : prices.entrySet()) {
            if (!(entry.getValue() instanceof Number number)) continue;
            if (matchesEcoType(entry.getKey())) {
                double d = number.doubleValue();
                return d > 0 ? d : -1;
            }
        }
        if (!prices.isEmpty()) {
            for (Object key : prices.keySet()) {
                String d = describe(key);
                if (warned.add(d)) {
                    plugin.getLogger().warning("ESG tra ve gia voi loai tien '" + d + "' khong khop pricing.eco-type='"
                            + plugin.settings().ecoType + "'. Neu day la loai tien ban muon, hay sua config.");
                }
            }
        }
        return -1;
    }

    private boolean matchesEcoType(Object key) {
        return describe(key).toUpperCase(Locale.ROOT).contains(plugin.settings().ecoType);
    }

    private static String describe(Object key) {
        StringBuilder sb = new StringBuilder(String.valueOf(key));
        for (String name : new String[]{"name", "getType", "getName", "getId"}) {
            try {
                Object r = key.getClass().getMethod(name).invoke(key);
                if (r != null) sb.append(' ').append(r);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return sb.toString();
    }
}
