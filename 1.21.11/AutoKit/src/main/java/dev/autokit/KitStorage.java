package dev.autokit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/** Moi nguoi choi 1 file: plugins/AutoKit/kits/UUID.yml. Ghi truc tiep (it khi xay ra) bang atomic write. */
public final class KitStorage {

    private static final Pattern ID = Pattern.compile("^[a-z0-9_-]{1,32}$");

    private final AutoKitPlugin plugin;
    private final Path dir;

    private final Map<UUID, Map<String, Kit>> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Map<String, Kit>> eldest) {
            return size() > 128;
        }
    };

    public KitStorage(AutoKitPlugin plugin) {
        this.plugin = plugin;
        this.dir = plugin.getDataFolder().toPath().resolve("kits");
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public Map<String, Kit> kitsOf(UUID owner) {
        Map<String, Kit> kits = cache.get(owner);
        if (kits == null) {
            kits = load(owner);
            cache.put(owner, kits);
        }
        return kits;
    }

    public Kit get(UUID owner, String id) {
        return kitsOf(owner).get(id);
    }

    public void save(Kit kit) throws IOException {
        Map<String, Kit> kits = kitsOf(kit.owner());
        Kit previous = kits.put(kit.id(), kit);
        try {
            write(kit.owner(), kits);
        } catch (IOException | RuntimeException e) {
            if (previous == null) kits.remove(kit.id());
            else kits.put(kit.id(), previous);
            throw e;
        }
    }

    public boolean delete(UUID owner, String id) throws IOException {
        Map<String, Kit> kits = kitsOf(owner);
        Kit removed = kits.remove(id);
        if (removed == null) return false;
        try {
            write(owner, kits);
        } catch (IOException | RuntimeException e) {
            kits.put(id, removed);
            throw e;
        }
        return true;
    }

    private Map<String, Kit> load(UUID owner) {
        Map<String, Kit> out = new TreeMap<>();
        Path file = dir.resolve(owner + ".yml");
        if (!Files.exists(file)) return out;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection kits = yml.getConfigurationSection("kits");
        if (kits == null) return out;

        for (String id : kits.getKeys(false)) {
            ConfigurationSection ks = kits.getConfigurationSection(id);
            if (ks == null) continue;
            Map<Integer, ItemStack> items = new TreeMap<>();
            ConfigurationSection is = ks.getConfigurationSection("items");
            if (is != null) {
                for (String slotKey : is.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotKey);
                        byte[] data = Base64.getDecoder().decode(is.getString(slotKey, ""));
                        items.put(slot, ItemStack.deserializeBytes(data));
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Khong doc duoc item o slot " + slotKey + " cua kit " + id
                                + " (" + owner + "): " + ex.getMessage());
                    }
                }
            }
            out.put(id, new Kit(id, owner, items, ks.getLong("created")));
        }
        return out;
    }

    private void write(UUID owner, Map<String, Kit> kits) throws IOException {
        YamlConfiguration yml = new YamlConfiguration();
        for (Kit kit : kits.values()) {
            String base = "kits." + kit.id();
            yml.set(base + ".created", kit.createdAt());
            for (Map.Entry<Integer, ItemStack> e : kit.items().entrySet()) {
                String b64 = Base64.getEncoder().encodeToString(e.getValue().serializeAsBytes());
                yml.set(base + ".items." + e.getKey(), b64);
            }
        }
        FileUtil.writeAtomic(dir.resolve(owner + ".yml"), yml.saveToString(), true);
    }
}
