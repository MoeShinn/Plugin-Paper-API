package com.example.ultimateweapons;

import com.example.ultimateweapons.addon.AddonManager;
import com.example.ultimateweapons.command.UwCommand;
import com.example.ultimateweapons.listener.WeaponListener;
import com.example.ultimateweapons.manager.ChargeManager;
import com.example.ultimateweapons.manager.CooldownManager;
import com.example.ultimateweapons.weapon.BehaviorRegistry;
import com.example.ultimateweapons.weapon.ItemKeys;
import com.example.ultimateweapons.weapon.WeaponRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/**
 * Class chính của plugin UltimateWeapons (lõi + bộ nạp addon, không có vũ khí cụ thể).
 */
public class UltimateWeaponsPlugin extends JavaPlugin {

    /** Phiên bản API dành cho addon. addon.yml phải khai báo api-version bằng đúng số này. */
    public static final int ADDON_API_VERSION = 1;

    /** Kết quả một lần nạp toàn bộ (onEnable hoặc /uw reload). */
    public record ReloadResult(int weaponCount, int addonCount,
                               List<WeaponRegistry.LoadError> weaponErrors,
                               List<AddonManager.AddonError> addonErrors) {
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ItemKeys itemKeys;
    private CooldownManager cooldownManager;
    private ChargeManager chargeManager;
    private WeaponRegistry weaponRegistry;
    private AddonManager addonManager;
    private WeaponListener weaponListener;

    @Override
    public void onEnable() {
        // Ghi config.yml mặc định nếu chưa có
        saveDefaultConfig();

        // Khởi tạo theo đúng thứ tự phụ thuộc
        this.itemKeys = new ItemKeys(this);
        this.cooldownManager = new CooldownManager();
        this.chargeManager = new ChargeManager(this);
        this.weaponRegistry = new WeaponRegistry(this);
        this.addonManager = new AddonManager(this);

        // Listener duy nhất + task onTick duy nhất
        this.weaponListener = new WeaponListener(this);
        getServer().getPluginManager().registerEvents(this.weaponListener, this);
        this.weaponListener.start();

        // built-in -> addon -> weapons/*.yml
        ReloadResult result = loadEverything();
        logResult(result);

        // Trường hợp plugin được load khi server đang chạy: người chơi có thể đã mặc sẵn vũ khí
        this.weaponListener.refreshAllEquipped();

        PluginCommand command = getCommand("uw");
        if (command == null) {
            getLogger().severe("Không tìm thấy lệnh 'uw' trong plugin.yml, tắt plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        UwCommand uwCommand = new UwCommand(this);
        command.setExecutor(uwCommand);
        command.setTabCompleter(uwCommand);
    }

    @Override
    public void onDisable() {
        // Dừng task onTick trước để không có hook nào chạy xen vào lúc dọn
        if (this.weaponListener != null) {
            this.weaponListener.stop();
        }
        // Bước 1-2 của quy trình reload: cleanup, xóa instance, gỡ addon
        releaseWeapons();
        if (this.cooldownManager != null) {
            this.cooldownManager.clearAll();
        }
        // Chốt chặn cuối: hủy mọi task còn sót của plugin
        Bukkit.getScheduler().cancelTasks(this);
    }

    /**
     * Reload toàn bộ theo thứ tự bắt buộc:
     * 1) cleanup + hủy charge  2) xóa instance, gỡ addon, xóa cache  3) reload config.yml
     * 4) built-in, addon, weapons/*.yml  5) báo kết quả.
     */
    public ReloadResult reloadAll() {
        releaseWeapons();
        reloadConfig();
        ReloadResult result = loadEverything();
        logResult(result);
        this.weaponListener.resetErrorReports();
        // Người đang mặc vũ khí ở slot giáp cần onEquip lại với instance mới
        this.weaponListener.refreshAllEquipped();
        return result;
    }

    /** Bước 1-2: onPlayerCleanup trên instance cũ, hủy charge, xóa instance, gỡ addon. */
    private void releaseWeapons() {
        if (this.chargeManager != null) {
            this.chargeManager.cancelAll();
        }
        if (this.weaponRegistry != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                this.weaponRegistry.cleanupPlayer(player);
            }
            this.weaponRegistry.clear();
        }
        if (this.addonManager != null) {
            this.addonManager.unloadAll();
        }
    }

    /** Bước 4: đăng ký built-in, nạp addon, quét weapons/*.yml dựng instance. */
    private ReloadResult loadEverything() {
        BehaviorRegistry.bootstrap();
        this.weaponRegistry.copyDefaultWeapons();
        List<AddonManager.AddonError> addonErrors = this.addonManager.loadAll();
        WeaponRegistry.LoadResult weapons = this.weaponRegistry.load();
        return new ReloadResult(weapons.loaded(), this.addonManager.getLoadedAddons().size(),
                weapons.errors(), addonErrors);
    }

    private void logResult(ReloadResult result) {
        getLogger().info("Đã load " + result.weaponCount() + " vũ khí từ " + result.addonCount() + " addon.");
        for (WeaponRegistry.LoadError error : result.weaponErrors()) {
            getLogger().warning("Bỏ qua file vũ khí '" + error.file() + "': " + error.reason());
        }
    }

    // ------------------------------------------------------------------
    // Message helper (MiniMessage, lấy từ config.yml)
    // ------------------------------------------------------------------

    /** Message có prefix. */
    public Component message(String key, TagResolver... resolvers) {
        String prefix = getConfig().getString("prefix", "");
        return MM.deserialize(prefix).append(rawMessage(key, resolvers));
    }

    /** Message không prefix (dùng cho các dòng danh sách). */
    public Component rawMessage(String key, TagResolver... resolvers) {
        String raw = getConfig().getString("messages." + key);
        if (raw == null) {
            return MM.deserialize("<red>Thiếu message trong config.yml: messages." + key);
        }
        return MM.deserialize(raw, resolvers);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(message(key, resolvers));
    }

    /** In log khi bật debug trong config.yml. */
    public void debug(String text) {
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("[DEBUG] " + text);
        }
    }

    /** File jar của plugin (getFile() của JavaPlugin là protected nên phải bọc lại). */
    public File getPluginJar() {
        return getFile();
    }

    // ------------------------------------------------------------------
    // Getter cho các manager (vũ khí dùng qua đây)
    // ------------------------------------------------------------------

    public WeaponRegistry getWeaponRegistry() {
        return weaponRegistry;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public ChargeManager getChargeManager() {
        return chargeManager;
    }

    public ItemKeys getItemKeys() {
        return itemKeys;
    }

    /** Nội bộ lõi, addon không được dùng. */
    public AddonManager getAddonManager() {
        return addonManager;
    }

    public WeaponListener getWeaponListener() {
        return weaponListener;
    }
}
