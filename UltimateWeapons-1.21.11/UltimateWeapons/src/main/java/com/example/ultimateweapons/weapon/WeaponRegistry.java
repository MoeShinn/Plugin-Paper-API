package com.example.ultimateweapons.weapon;

import com.example.ultimateweapons.UltimateWeaponsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Map id -> vũ khí, dựng từ plugins/UltimateWeapons/weapons/*.yml.
 * Lỗi ở một file chỉ làm bỏ qua file đó.
 */
public final class WeaponRegistry {

    /** Kết quả một lần nạp. */
    public record LoadResult(int loaded, List<LoadError> errors) {
    }

    /** Một file bị bỏ qua và lý do. */
    public record LoadError(String file, String reason) {
    }

    private record Entry(WeaponDefinition def, UltimateWeapon weapon, String source) {
    }

    /** Lỗi nạp một file (thông điệp đã là tiếng Việt, hiển thị thẳng cho người dùng). */
    private static final class WeaponLoadException extends Exception {
        WeaponLoadException(String message) {
            super(message);
        }
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_\\-]+");
    private static final String[] REQUIRED_KEYS = {"id", "type", "display-name", "description", "material"};
    private static final String JAR_WEAPONS_DIR = "weapons/";

    private final UltimateWeaponsPlugin plugin;
    // Không phân biệt hoa thường để /uw give hoạt động thoải mái
    private Map<String, Entry> entries = Collections.emptyMap();

    public WeaponRegistry(UltimateWeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public File getWeaponsDir() {
        return new File(plugin.getDataFolder(), "weapons");
    }

    // ------------------------------------------------------------------
    // Truy vấn
    // ------------------------------------------------------------------

    /** Lấy vũ khí theo id. null nếu không có. */
    public UltimateWeapon get(String id) {
        if (id == null) {
            return null;
        }
        Entry entry = entries.get(id);
        return entry == null ? null : entry.weapon();
    }

    /** Lấy definition theo id. null nếu không có. */
    public WeaponDefinition getDefinition(String id) {
        if (id == null) {
            return null;
        }
        Entry entry = entries.get(id);
        return entry == null ? null : entry.def();
    }

    /** Nguồn của vũ khí: "built-in" hoặc tên addon. null nếu không có vũ khí đó. */
    public String getSource(String id) {
        if (id == null) {
            return null;
        }
        Entry entry = entries.get(id);
        return entry == null ? null : entry.source();
    }

    /** Toàn bộ vũ khí (sắp theo id). */
    public Collection<UltimateWeapon> all() {
        List<UltimateWeapon> list = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            list.add(entry.weapon());
        }
        return Collections.unmodifiableList(list);
    }

    /** Toàn bộ definition (sắp theo id). */
    public Collection<WeaponDefinition> definitions() {
        List<WeaponDefinition> list = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            list.add(entry.def());
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Nhận diện vũ khí từ item bằng PDC weapon_id.
     * null nếu không phải vũ khí hoặc vũ khí/addon của nó không còn tồn tại (chỉ bỏ qua, không ném lỗi).
     */
    public UltimateWeapon weaponOf(ItemStack item) {
        String id = plugin.getItemKeys().readWeaponId(item);
        return id == null ? null : get(id);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void clear() {
        entries = Collections.emptyMap();
    }

    /** Gọi onPlayerCleanup trên mọi vũ khí. Lỗi của một vũ khí không ảnh hưởng vũ khí khác. */
    public void cleanupPlayer(Player player) {
        if (player == null) {
            return;
        }
        for (Entry entry : entries.values()) {
            try {
                entry.weapon().onPlayerCleanup(player);
            } catch (Exception | LinkageError ex) {
                plugin.getLogger().log(Level.SEVERE,
                        "Lỗi trong onPlayerCleanup của vũ khí '" + entry.def().id() + "'", ex);
            }
        }
    }

    // ------------------------------------------------------------------
    // Copy yml mặc định từ jar
    // ------------------------------------------------------------------

    /**
     * Quét các entry của jar lõi, copy mọi weapons/*.yml và weapons/*.yml.disabled ra thư mục plugin,
     * chỉ ghi nếu file chưa tồn tại (không đè chỉnh sửa của người dùng).
     */
    public void copyDefaultWeapons() {
        File jarFile = plugin.getPluginJar();
        if (jarFile == null || !jarFile.isFile()) {
            plugin.getLogger().warning("Không xác định được file jar của plugin, bỏ qua copy yml mặc định.");
            return;
        }
        try (JarFile jar = new JarFile(jarFile)) {
            copyWeaponYmlsFromJar(jar, true);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Không đọc được jar để copy yml mặc định", ex);
        }
    }

    /**
     * Copy mọi weapons/*.yml (và *.yml.disabled nếu includeDisabled) trong jar ra thư mục weapons,
     * chỉ khi file đích chưa tồn tại. Dùng cho cả jar lõi và addon jar. Trả về số file đã copy.
     */
    public int copyWeaponYmlsFromJar(JarFile jar, boolean includeDisabled) {
        File dir = getWeaponsDir();
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Không tạo được thư mục " + dir.getPath());
            return 0;
        }
        int copied = 0;
        Enumeration<JarEntry> en = jar.entries();
        while (en.hasMoreElements()) {
            JarEntry entry = en.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (!name.startsWith(JAR_WEAPONS_DIR)) {
                continue;
            }
            String fileName = name.substring(JAR_WEAPONS_DIR.length());
            // Chỉ lấy file nằm trực tiếp trong weapons/
            if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\")) {
                continue;
            }
            String lower = fileName.toLowerCase(Locale.ROOT);
            boolean ok = lower.endsWith(".yml") || (includeDisabled && lower.endsWith(".yml.disabled"));
            if (!ok) {
                continue;
            }
            File target = new File(dir, fileName);
            if (target.exists()) {
                continue;
            }
            try (InputStream in = jar.getInputStream(entry)) {
                Files.copy(in, target.toPath());
                copied++;
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Không copy được " + fileName, ex);
            }
        }
        return copied;
    }

    // ------------------------------------------------------------------
    // Nạp / reload
    // ------------------------------------------------------------------

    /** Nạp (hoặc nạp lại) mọi weapons/*.yml và tạo instance mới. Không bao giờ ném exception. */
    public LoadResult load() {
        Map<String, Entry> loaded = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<LoadError> errors = new ArrayList<>();

        File dir = getWeaponsDir();
        if (!dir.exists() && !dir.mkdirs()) {
            errors.add(new LoadError(dir.getName(), "không tạo được thư mục weapons"));
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            files = new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            try {
                Entry entry = loadFile(file);
                if (loaded.containsKey(entry.def().id())) {
                    throw new WeaponLoadException("id '" + entry.def().id() + "' bị trùng với một file khác");
                }
                loaded.put(entry.def().id(), entry);
            } catch (WeaponLoadException ex) {
                errors.add(new LoadError(file.getName(), ex.getMessage()));
            } catch (Exception | LinkageError ex) {
                errors.add(new LoadError(file.getName(), "lỗi không xác định: " + ex));
            }
        }

        this.entries = Collections.unmodifiableMap(loaded);
        return new LoadResult(loaded.size(), Collections.unmodifiableList(errors));
    }

    private Entry loadFile(File file) throws WeaponLoadException {
        YamlConfiguration yml = new YamlConfiguration();
        try {
            yml.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            throw new WeaponLoadException("yml hỏng: " + firstLine(ex.getMessage()));
        }

        // Kiểm tra khóa bắt buộc
        for (String key : REQUIRED_KEYS) {
            String value = yml.getString(key);
            if (value == null) {
                throw new WeaponLoadException("thiếu khóa bắt buộc '" + key + "'");
            }
            if (value.isBlank()) {
                throw new WeaponLoadException("khóa '" + key + "' đang để trống");
            }
        }

        String id = yml.getString("id").trim();
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new WeaponLoadException("id '" + id + "' chỉ được chứa chữ, số, '_' và '-'");
        }

        String type = yml.getString("type").trim();
        BehaviorRegistry.Factory factory = BehaviorRegistry.get(type);
        if (factory == null) {
            String known = BehaviorRegistry.types().isEmpty()
                    ? "(chưa có type nào, hãy thêm addon vào thư mục addons)"
                    : String.join(", ", BehaviorRegistry.types());
            throw new WeaponLoadException("type '" + type + "' không tồn tại. Các type hiện có: " + known);
        }
        String source = BehaviorRegistry.sourceOf(type);

        String materialName = yml.getString("material").trim();
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir() || !material.isItem()) {
            throw new WeaponLoadException("material '" + materialName + "' không hợp lệ");
        }

        Component displayName;
        try {
            displayName = MM.deserialize(yml.getString("display-name"));
        } catch (Exception ex) {
            throw new WeaponLoadException("display-name không phải MiniMessage hợp lệ: " + firstLine(ex.getMessage()));
        }

        WeaponDefinition def = new WeaponDefinition(id, type.toLowerCase(Locale.ROOT), displayName,
                yml.getString("description"), material, yml);

        UltimateWeapon weapon;
        try {
            weapon = factory.create(plugin, def);
        } catch (Exception | LinkageError ex) {
            throw new WeaponLoadException("không tạo được vũ khí type '" + type + "': " + ex);
        }
        if (weapon == null) {
            throw new WeaponLoadException("factory của type '" + type + "' trả về null");
        }
        return new Entry(def, weapon, source == null ? BehaviorRegistry.BUILTIN_SOURCE : source);
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "(không rõ)";
        }
        int newline = text.indexOf('\n');
        return (newline >= 0 ? text.substring(0, newline) : text).trim();
    }
}
