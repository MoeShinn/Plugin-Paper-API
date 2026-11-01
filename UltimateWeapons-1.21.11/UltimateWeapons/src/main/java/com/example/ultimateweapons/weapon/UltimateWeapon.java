package com.example.ultimateweapons.weapon;

import com.example.ultimateweapons.UltimateWeaponsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Lớp cha của mọi vũ khí. Subclass chỉ override các hook cần dùng.
 * Mọi hook chạy trên MAIN THREAD.
 *
 * Nhớ: đọc chỉ số qua def.getDouble(...) tại thời điểm dùng, không cache vào field.
 */
public abstract class UltimateWeapon {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    protected final UltimateWeaponsPlugin plugin;
    protected final WeaponDefinition def;

    protected UltimateWeapon(UltimateWeaponsPlugin plugin, WeaponDefinition def) {
        this.plugin = plugin;
        this.def = def;
    }

    public final String id() {
        return def.id();
    }

    /**
     * Dựng item từ definition: material, tên, lore (description + lore trong yml),
     * custom-model-data, unbreakable, hide-attributes, ghi PDC weapon_id, rồi gọi customizeItem(meta).
     */
    public final ItemStack createItem() {
        ItemStack item = new ItemStack(def.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Tên (tắt chữ nghiêng mặc định của item)
        meta.displayName(noItalic(def.displayName()));

        // Lore = description + lore tùy chọn
        List<Component> lore = new ArrayList<>();
        String description = def.description();
        if (description != null && !description.isBlank()) {
            lore.add(noItalic(MM.deserialize(description)));
        }
        for (String line : def.getStringList("lore")) {
            lore.add(noItalic(MM.deserialize(line)));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }

        // custom-model-data (tùy chọn) dùng component mới, không dùng API deprecated
        if (def.config().isSet("custom-model-data")) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) def.getInt("custom-model-data", 0)));
            meta.setCustomModelDataComponent(cmd);
        }

        if (def.getBoolean("unbreakable", false)) {
            meta.setUnbreakable(true);
        }
        if (def.getBoolean("hide-attributes", false)) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }

        // Ghi id vũ khí vào PDC (cách duy nhất để nhận diện vũ khí)
        meta.getPersistentDataContainer().set(plugin.getItemKeys().weaponId(), PersistentDataType.STRING, def.id());

        // Cho subclass chỉnh thêm (CrossbowMeta, ẩn attribute...)
        customizeItem(meta);

        item.setItemMeta(meta);
        return item;
    }

    private static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Override khi cần chỉnh thêm ItemMeta lúc tạo item. */
    protected void customizeItem(ItemMeta meta) {
    }

    // ------------------------------------------------------------------
    // Các hook (mặc định rỗng)
    // ------------------------------------------------------------------

    /** Click trái bằng tay chính khi đang cầm vũ khí. Có thể event.setCancelled(true). */
    public void onLeftClick(Player player, ItemStack item, PlayerInteractEvent event) {
    }

    /** Click phải bằng tay chính khi đang cầm vũ khí. Có thể event.setCancelled(true). */
    public void onRightClick(Player player, ItemStack item, PlayerInteractEvent event) {
    }

    /** Bật/tắt sneak khi vũ khí ở tay chính. sneaking = trạng thái mới. */
    public void onSneak(Player player, ItemStack item, boolean sneaking) {
    }

    /** Bắn cung/nỏ là vũ khí. Lõi đã gắn tag (weapon_id + chủ nhân) vào projectile trước khi gọi. */
    public void onShoot(Player player, ItemStack item, EntityShootBowEvent event) {
    }

    /** Projectile có tag chạm block/entity, chủ nhân đang online. Tự projectile.remove() nếu cần. */
    public void onProjectileHit(Player owner, Projectile projectile, ProjectileHitEvent event) {
    }

    /** Gọi MỖI TICK khi vũ khí đang ở tay chính/tay phụ hoặc slot giáp. */
    public void onTick(Player player, ItemStack item, EquipmentSlot slot) {
    }

    /** Vũ khí được mặc vào slot giáp (hoặc đã mặc sẵn khi join/reload). */
    public void onEquip(Player player, ItemStack item) {
    }

    /** Vũ khí được tháo khỏi slot giáp. */
    public void onUnequip(Player player, ItemStack item) {
    }

    /** Bật/tắt bay lượn khi vũ khí ở slot ngực. gliding = trạng thái mới. */
    public void onGlideToggle(Player player, ItemStack item, boolean gliding) {
    }

    /**
     * Dọn mọi state/task/bossbar của người chơi. Được gọi trên MỌI vũ khí khi người chơi thoát, chết,
     * đổi world, và khi reload/disable. Phải chịu được việc bị gọi dù người đó không dùng vũ khí này.
     */
    public void onPlayerCleanup(Player player) {
    }
}
