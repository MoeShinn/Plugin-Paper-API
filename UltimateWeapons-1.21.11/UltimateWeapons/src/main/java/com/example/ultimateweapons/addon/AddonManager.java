package com.example.ultimateweapons.addon;

import com.example.ultimateweapons.UltimateWeaponsPlugin;
import com.example.ultimateweapons.weapon.BehaviorRegistry;
import com.example.ultimateweapons.weapon.UltimateWeapon;
import com.example.ultimateweapons.weapon.WeaponDefinition;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * NỘI BỘ LÕI: nạp/gỡ addon jar lúc chạy. Addon không được dùng class này.
 *
 * Mỗi lần nạp: copy jar từ addons/ sang .addon-cache/ (tên duy nhất, để người dùng thay jar được
 * trên Windows), rồi nạp từ bản copy bằng một URLClassLoader riêng (parent = classloader của plugin lõi).
 * Lỗi của một addon chỉ làm bỏ qua addon/vũ khí đó.
 */
public final class AddonManager {

    /** Thông tin một addon đã nạp thành công. */
    public record AddonInfo(String name, String version, List<String> types, String jarName) {
    }

    /** Một lỗi addon (source là tên addon hoặc tên file jar nếu chưa đọc được tên). */
    public record AddonError(String source, String reason) {
    }

    private static final class LoadedAddon {
        final AddonInfo info;
        final URLClassLoader loader;

        LoadedAddon(AddonInfo info, URLClassLoader loader) {
            this.info = info;
            this.loader = loader;
        }
    }

    /** Lỗi nghiêm trọng khiến cả addon bị bỏ qua (thông điệp hiển thị thẳng cho người dùng). */
    private static final class AddonException extends Exception {
        AddonException(String message) {
            super(message);
        }
    }

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9_\\-]+");

    private final UltimateWeaponsPlugin plugin;
    private final List<LoadedAddon> loaded = new ArrayList<>();
    private List<AddonError> lastErrors = Collections.emptyList();

    public AddonManager(UltimateWeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public File getAddonsDir() {
        return new File(plugin.getDataFolder(), "addons");
    }

    public File getCacheDir() {
        return new File(plugin.getDataFolder(), ".addon-cache");
    }

    /** Các addon đã nạp thành công (theo thứ tự nạp). */
    public List<AddonInfo> getLoadedAddons() {
        List<AddonInfo> list = new ArrayList<>(loaded.size());
        for (LoadedAddon addon : loaded) {
            list.add(addon.info);
        }
        return Collections.unmodifiableList(list);
    }

    /** Lỗi của lần nạp gần nhất. */
    public List<AddonError> getLastErrors() {
        return lastErrors;
    }

    // ------------------------------------------------------------------
    // Nạp
    // ------------------------------------------------------------------

    /**
     * Quét addons/*.jar và nạp từng addon. Không bao giờ ném exception.
     * Phải gọi SAU BehaviorRegistry.bootstrap() (để type built-in được ưu tiên).
     */
    public List<AddonError> loadAll() {
        List<AddonError> errors = new ArrayList<>();
        File dir = getAddonsDir();
        if (!dir.exists() && !dir.mkdirs()) {
            errors.add(new AddonError(dir.getName(), "không tạo được thư mục addons"));
        }
        clearCache();

        File[] jars = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null) {
            jars = new File[0];
        }
        Arrays.sort(jars, Comparator.comparing(File::getName));

        for (File jar : jars) {
            if (!jar.isFile()) {
                continue;
            }
            try {
                loadJar(jar, errors);
            } catch (AddonException ex) {
                errors.add(new AddonError(jar.getName(), ex.getMessage()));
            } catch (Exception | LinkageError ex) {
                errors.add(new AddonError(jar.getName(), "lỗi không xác định: " + ex));
            }
        }

        for (AddonError error : errors) {
            plugin.getLogger().warning("Addon '" + error.source() + "': " + error.reason());
        }
        this.lastErrors = Collections.unmodifiableList(errors);
        return this.lastErrors;
    }

    private void loadJar(File original, List<AddonError> errors) throws AddonException {
        String jarName = original.getName();

        // 1) Copy sang cache với tên duy nhất, không nạp trực tiếp từ addons/
        File cacheDir = getCacheDir();
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new AddonException("không tạo được thư mục .addon-cache");
        }
        File cacheFile = new File(cacheDir, System.nanoTime() + "-" + jarName);
        try {
            Files.copy(original.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new AddonException("không copy được sang cache: " + ex.getMessage());
        }

        URLClassLoader loader = null;
        boolean success = false;
        try {
            // 2) Đọc và kiểm tra addon.yml
            YamlConfiguration yml = readAddonYml(cacheFile);

            String name = yml.getString("name");
            if (name == null || name.isBlank()) {
                throw new AddonException("addon.yml thiếu khóa 'name'");
            }
            name = name.trim();
            if (!NAME_PATTERN.matcher(name).matches()) {
                throw new AddonException("tên addon '" + name + "' chỉ được chứa a-z 0-9 _ -");
            }
            for (LoadedAddon other : loaded) {
                if (other.info.name().equals(name)) {
                    throw new AddonException("tên addon '" + name + "' bị trùng với addon '"
                            + other.info.jarName() + "' đã nạp");
                }
            }
            String version = yml.getString("version", "?");

            int apiVersion = yml.getInt("api-version", -1);
            if (apiVersion != UltimateWeaponsPlugin.ADDON_API_VERSION) {
                throw new AddonException("api-version " + apiVersion + " không khớp (lõi cần "
                        + UltimateWeaponsPlugin.ADDON_API_VERSION + ")");
            }

            List<Map<?, ?>> declared = yml.getMapList("weapons");
            if (declared.isEmpty()) {
                throw new AddonException("addon.yml không khai báo vũ khí nào trong 'weapons'");
            }

            // 3) Mỗi addon một URLClassLoader riêng, parent là classloader của plugin lõi
            try {
                loader = new URLClassLoader(new URL[]{cacheFile.toURI().toURL()}, plugin.getClass().getClassLoader());
            } catch (IOException ex) {
                throw new AddonException("không tạo được classloader: " + ex.getMessage());
            }

            // 4) Nạp và đăng ký từng type
            List<String> registeredTypes = new ArrayList<>();
            for (Map<?, ?> entry : declared) {
                String type = asString(entry.get("type"));
                String className = asString(entry.get("class"));
                if (type == null || className == null) {
                    errors.add(new AddonError(name, "một mục trong 'weapons' thiếu 'type' hoặc 'class'"));
                    continue;
                }
                type = type.toLowerCase(Locale.ROOT);

                if (BehaviorRegistry.has(type)) {
                    errors.add(new AddonError(name, "type '" + type + "' đã được đăng ký bởi '"
                            + BehaviorRegistry.sourceOf(type) + "', bỏ qua type này"));
                    continue;
                }

                Class<?> clazz;
                try {
                    clazz = Class.forName(className, true, loader);
                } catch (ClassNotFoundException ex) {
                    errors.add(new AddonError(name, "type '" + type + "': không tìm thấy class " + className));
                    continue;
                } catch (LinkageError ex) {
                    errors.add(new AddonError(name, "type '" + type + "': lỗi khi nạp class " + className + ": " + ex));
                    continue;
                }

                if (!UltimateWeapon.class.isAssignableFrom(clazz) || Modifier.isAbstract(clazz.getModifiers())) {
                    errors.add(new AddonError(name, "type '" + type + "': class " + className
                            + " phải là class cụ thể kế thừa UltimateWeapon"));
                    continue;
                }

                final Constructor<?> constructor = findConstructor(clazz);
                if (constructor == null) {
                    errors.add(new AddonError(name, "type '" + type + "': class " + className
                            + " thiếu constructor công khai (UltimateWeaponsPlugin, WeaponDefinition)"));
                    continue;
                }

                BehaviorRegistry.registerFromAddon(name, type, (p, d) -> instantiate(constructor, p, d));
                registeredTypes.add(type);
            }

            if (registeredTypes.isEmpty()) {
                throw new AddonException("không có type nào nạp được");
            }

            // 5) Copy weapons/*.yml trong addon ra thư mục weapons nếu chưa có
            try (JarFile jarFile = new JarFile(cacheFile)) {
                plugin.getWeaponRegistry().copyWeaponYmlsFromJar(jarFile, false);
            } catch (IOException ex) {
                errors.add(new AddonError(name, "không copy được yml vũ khí: " + ex.getMessage()));
            }

            AddonInfo info = new AddonInfo(name, version, Collections.unmodifiableList(registeredTypes), jarName);
            loaded.add(new LoadedAddon(info, loader));
            success = true;
            plugin.getLogger().info("Đã nạp addon '" + name + "' v" + version + " (" + registeredTypes.size()
                    + " type) từ " + jarName);
        } finally {
            if (!success) {
                // Addon thất bại: trả lại mọi thứ đã đăng ký dở và giải phóng file
                if (loader != null) {
                    try {
                        loader.close();
                    } catch (IOException ignored) {
                        // bỏ qua
                    }
                }
                if (!cacheFile.delete()) {
                    cacheFile.deleteOnExit();
                }
            }
        }
    }

    private YamlConfiguration readAddonYml(File cacheFile) throws AddonException {
        try (JarFile jar = new JarFile(cacheFile)) {
            JarEntry entry = jar.getJarEntry("addon.yml");
            if (entry == null) {
                throw new AddonException("thiếu addon.yml ở gốc jar");
            }
            try (InputStream in = jar.getInputStream(entry);
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                YamlConfiguration yml = new YamlConfiguration();
                yml.load(reader);
                return yml;
            }
        } catch (InvalidConfigurationException ex) {
            throw new AddonException("addon.yml hỏng: " + firstLine(ex.getMessage()));
        } catch (IOException ex) {
            throw new AddonException("không đọc được jar: " + ex.getMessage());
        }
    }

    private static Constructor<?> findConstructor(Class<?> clazz) {
        try {
            return clazz.getConstructor(UltimateWeaponsPlugin.class, WeaponDefinition.class);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private static UltimateWeapon instantiate(Constructor<?> constructor, UltimateWeaponsPlugin plugin,
                                              WeaponDefinition def) {
        try {
            return (UltimateWeapon) constructor.newInstance(plugin, def);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException("constructor ném lỗi: " + cause, cause);
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new IllegalStateException("không khởi tạo được vũ khí: " + ex, ex);
        }
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "(không rõ)";
        }
        int newline = text.indexOf('\n');
        return (newline >= 0 ? text.substring(0, newline) : text).trim();
    }

    // ------------------------------------------------------------------
    // Gỡ
    // ------------------------------------------------------------------

    /**
     * Gỡ mọi addon: unregisterAddon, đóng classloader, xóa cache.
     * Phải gọi SAU KHI đã gọi onPlayerCleanup và xóa instance trong WeaponRegistry.
     */
    public void unloadAll() {
        for (LoadedAddon addon : loaded) {
            BehaviorRegistry.unregisterAddon(addon.info.name());
            try {
                addon.loader.close();
            } catch (IOException ex) {
                plugin.getLogger().warning("Không đóng được classloader của addon '" + addon.info.name() + "': " + ex.getMessage());
            }
        }
        loaded.clear();
        clearCache();
    }

    private void clearCache() {
        File[] files = getCacheDir().listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && !file.delete()) {
                plugin.debug("Chưa xóa được file cache: " + file.getName());
                file.deleteOnExit();
            }
        }
    }
}
