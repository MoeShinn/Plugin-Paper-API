package com.example.ultimateweapons.listener;

import com.example.ultimateweapons.UltimateWeaponsPlugin;
import com.example.ultimateweapons.util.ExplosionUtil;
import com.example.ultimateweapons.weapon.ItemKeys;
import com.example.ultimateweapons.weapon.UltimateWeapon;
import com.example.ultimateweapons.weapon.WeaponRegistry;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener DUY NHẤT của plugin + task lặp DUY NHẤT lo onTick.
 * Nhận diện vũ khí bằng PDC weapon_id, rồi gọi đúng hook.
 */
public final class WeaponListener implements Listener {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final EquipmentSlot[] TICK_SLOTS = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final UltimateWeaponsPlugin plugin;
    private BukkitTask tickTask;
    private int tickCounter = 0;

    // Chống gọi trùng click: uuid -> (tick << 2 | loại click)
    private final Map<UUID, Long> lastClick = new HashMap<>();
    // Mỗi (vũ khí, hook) chỉ in stacktrace một lần để không spam console
    private final Set<String> reportedErrors = new HashSet<>();

    public WeaponListener(UltimateWeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Vòng đời
    // ------------------------------------------------------------------

    /** Bắt đầu task lặp duy nhất (1 tick). */
    public void start() {
        if (tickTask != null) {
            return;
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Dừng task và dọn state nội bộ. */
    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        lastClick.clear();
        reportedErrors.clear();
    }

    public void resetErrorReports() {
        reportedErrors.clear();
    }

    // ------------------------------------------------------------------
    // Task onTick (rẻ: chỉ đọc PDC của vài slot cho mỗi người chơi)
    // ------------------------------------------------------------------

    private void tick() {
        tickCounter++;
        WeaponRegistry registry = plugin.getWeaponRegistry();
        if (!registry.isEmpty()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isDead()) {
                    continue;
                }
                for (EquipmentSlot slot : TICK_SLOTS) {
                    ItemStack item = player.getInventory().getItem(slot);
                    UltimateWeapon weapon = registry.weaponOf(item);
                    if (weapon != null) {
                        safe(weapon, "onTick", () -> weapon.onTick(player, item, slot));
                    }
                }
            }
        }
        // Mỗi 1 phút dọn cooldown đã hết hạn để không rò bộ nhớ
        if (tickCounter % 1200 == 0) {
            plugin.getCooldownManager().purgeAllExpired();
        }
    }

    // ------------------------------------------------------------------
    // Tương tác
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        // Chỉ tay chính: tránh gọi trùng 2 lần (tay chính + tay phụ)
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) {
            return;
        }
        // Plugin bảo vệ vùng đã từ chối dùng item
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        ItemStack item = event.getItem();
        UltimateWeapon weapon = plugin.getWeaponRegistry().weaponOf(item);
        if (weapon == null) {
            return;
        }
        Player player = event.getPlayer();
        if (isDuplicateClick(player, left)) {
            return;
        }
        if (left) {
            safe(weapon, "onLeftClick", () -> weapon.onLeftClick(player, item, event));
        } else {
            safe(weapon, "onRightClick", () -> weapon.onRightClick(player, item, event));
        }
    }

    private boolean isDuplicateClick(Player player, boolean left) {
        long key = ((long) tickCounter << 2) | (left ? 1L : 2L);
        Long previous = lastClick.put(player.getUniqueId(), key);
        return previous != null && previous == key;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        UltimateWeapon weapon = plugin.getWeaponRegistry().weaponOf(item);
        if (weapon == null) {
            return;
        }
        boolean sneaking = event.isSneaking();
        safe(weapon, "onSneak", () -> weapon.onSneak(player, item, sneaking));
    }

    // ------------------------------------------------------------------
    // Cung / nỏ / projectile
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack bow = event.getBow();
        UltimateWeapon weapon = plugin.getWeaponRegistry().weaponOf(bow);
        if (weapon == null) {
            return;
        }
        // Gắn tag TRƯỚC khi gọi hook (theo hợp đồng)
        plugin.getItemKeys().tagProjectile(event.getProjectile(), weapon.id(), player.getUniqueId());
        safe(weapon, "onShoot", () -> weapon.onShoot(player, bow, event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        ItemKeys keys = plugin.getItemKeys();
        String weaponId = keys.readProjectileWeaponId(projectile);
        if (weaponId == null) {
            return;
        }
        UUID ownerId = keys.readProjectileOwner(projectile);
        if (ownerId == null) {
            return;
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            return;
        }
        UltimateWeapon weapon = plugin.getWeaponRegistry().get(weaponId);
        if (weapon == null) {
            return;
        }
        safe(weapon, "onProjectileHit", () -> weapon.onProjectileHit(owner, projectile, event));
    }

    // ------------------------------------------------------------------
    // Bay lượn / giáp
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack chest = player.getInventory().getChestplate();
        UltimateWeapon weapon = plugin.getWeaponRegistry().weaponOf(chest);
        if (weapon == null) {
            return;
        }
        boolean gliding = event.isGliding();
        safe(weapon, "onGlideToggle", () -> weapon.onGlideToggle(player, chest, gliding));
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        ItemStack oldItem = event.getOldItem();
        ItemStack newItem = event.getNewItem();
        ItemKeys keys = plugin.getItemKeys();
        String oldId = keys.readWeaponId(oldItem);
        String newId = keys.readWeaponId(newItem);
        // Cùng một vũ khí (ví dụ chỉ đổi độ bền) thì không coi là đổi trang bị
        if (oldId != null && oldId.equals(newId)) {
            return;
        }
        if (oldId != null) {
            UltimateWeapon oldWeapon = plugin.getWeaponRegistry().get(oldId);
            if (oldWeapon != null) {
                safe(oldWeapon, "onUnequip", () -> oldWeapon.onUnequip(player, oldItem));
            }
        }
        if (newId != null) {
            UltimateWeapon newWeapon = plugin.getWeaponRegistry().get(newId);
            if (newWeapon != null) {
                safe(newWeapon, "onEquip", () -> newWeapon.onEquip(player, newItem));
            }
        }
    }

    /** Gọi onEquip cho mọi vũ khí đang mặc ở slot giáp của người chơi. */
    public void refreshEquipped(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack item = player.getInventory().getItem(slot);
            UltimateWeapon weapon = plugin.getWeaponRegistry().weaponOf(item);
            if (weapon != null) {
                safe(weapon, "onEquip", () -> weapon.onEquip(player, item));
            }
        }
    }

    public void refreshAllEquipped() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshEquipped(player);
        }
    }

    // ------------------------------------------------------------------
    // Vòng đời người chơi: dọn state
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Vào server đã mặc sẵn vũ khí thì gọi onEquip ngay
        refreshEquipped(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cleanup(player);
        lastClick.remove(player.getUniqueId());
        // Giữ lại cooldown còn hiệu lực (chống thoát/vào để reset), chỉ bỏ cái đã hết hạn
        plugin.getCooldownManager().purgeExpired(player.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cleanup(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Nếu giữ đồ sau khi chết, giáp vẫn còn: gọi lại onEquip ở tick sau
        Bukkit.getScheduler().runTask(plugin, () -> refreshEquipped(player));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        cleanup(player);
        // State đã bị dọn, giáp vẫn đang mặc nên khôi phục bằng onEquip
        refreshEquipped(player);
    }

    private void cleanup(Player player) {
        plugin.getChargeManager().cancel(player);
        plugin.getWeaponRegistry().cleanupPlayer(player);
    }

    // ------------------------------------------------------------------
    // Sát thương nổ: chặn self-damage / mục tiêu không hợp lệ của ExplosionUtil
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (ExplosionUtil.shouldCancel(event)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Gọi hook an toàn: lỗi của một vũ khí không làm hỏng listener/vũ khí khác
    // ------------------------------------------------------------------

    private void safe(UltimateWeapon weapon, String hook, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            String key = weapon.id() + "#" + hook;
            if (reportedErrors.add(key)) {
                plugin.getLogger().log(Level.SEVERE,
                        "Lỗi khi gọi " + hook + " của vũ khí '" + weapon.id() + "' (chỉ báo đầy đủ một lần)", ex);
            } else {
                plugin.debug("Lỗi lặp lại ở " + key + ": " + ex);
            }
        }
    }
}
