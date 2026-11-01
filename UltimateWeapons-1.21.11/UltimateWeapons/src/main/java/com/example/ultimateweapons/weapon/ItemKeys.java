package com.example.ultimateweapons.weapon;

import com.example.ultimateweapons.UltimateWeaponsPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Nơi tập trung các NamespacedKey và hàm đọc/ghi PDC.
 * Truy cập qua plugin.getItemKeys().
 */
public final class ItemKeys {

    private final NamespacedKey weaponId;
    private final NamespacedKey projectileWeaponId;
    private final NamespacedKey projectileOwner;

    public ItemKeys(UltimateWeaponsPlugin plugin) {
        this.weaponId = new NamespacedKey(plugin, "weapon_id");
        this.projectileWeaponId = new NamespacedKey(plugin, "projectile_weapon_id");
        this.projectileOwner = new NamespacedKey(plugin, "projectile_owner");
    }

    /** Key lưu id vũ khí trên ItemStack. */
    public NamespacedKey weaponId() {
        return weaponId;
    }

    /** Key lưu id vũ khí trên entity (projectile, TNT...). */
    public NamespacedKey projectileWeaponId() {
        return projectileWeaponId;
    }

    /** Key lưu UUID chủ nhân trên entity. */
    public NamespacedKey projectileOwner() {
        return projectileOwner;
    }

    /** Đọc weapon_id từ item. Trả về null nếu item null, rỗng hoặc không phải vũ khí. */
    public String readWeaponId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(weaponId, PersistentDataType.STRING);
    }

    /** Gắn tag vũ khí + chủ nhân cho entity do vũ khí tự sinh (TNT, mũi tên phụ...). */
    public void tagProjectile(Entity entity, String weaponId, UUID owner) {
        if (entity == null || weaponId == null) {
            return;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(projectileWeaponId, PersistentDataType.STRING, weaponId);
        if (owner != null) {
            pdc.set(projectileOwner, PersistentDataType.STRING, owner.toString());
        }
    }

    /** Đọc id vũ khí từ entity. null nếu không có tag. */
    public String readProjectileWeaponId(Entity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getPersistentDataContainer().get(projectileWeaponId, PersistentDataType.STRING);
    }

    /** Đọc UUID chủ nhân từ entity. null nếu không có tag hoặc tag hỏng. */
    public UUID readProjectileOwner(Entity entity) {
        if (entity == null) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(projectileOwner, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
