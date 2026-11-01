package com.example.ultimateweapons.util;

import com.example.ultimateweapons.weapon.WeaponDefinition;
import org.bukkit.GameMode;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

/**
 * Mọi sát thương trực tiếp của vũ khí phải đi qua DamageUtil.hit.
 * Sát thương luôn được tính cho attacker (kill credit, rơi đồ, XP).
 * Đọc các khóa self-damage, ignore-iframes, ignore-armor trong yml của vũ khí.
 */
public final class DamageUtil {

    private DamageUtil() {
    }

    /**
     * Gây sát thương cho target, tính cho attacker.
     * Tự kiểm tra isValidTarget. Trả về true nếu đã gây damage.
     */
    public static boolean hit(Player attacker, LivingEntity target, double amount, WeaponDefinition def) {
        if (attacker == null || target == null || def == null) {
            return false;
        }
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return false;
        }
        if (!isValidTarget(attacker, target, def)) {
            return false;
        }

        // Bỏ qua giáp: dùng loại sát thương phép (bypass armor) nhưng vẫn gắn chủ nhân là attacker
        boolean ignoreArmor = def.getBoolean("ignore-armor", false);
        DamageType type = ignoreArmor ? DamageType.INDIRECT_MAGIC : DamageType.PLAYER_ATTACK;
        DamageSource source = DamageSource.builder(type)
                .withCausingEntity(attacker)
                .withDirectEntity(attacker)
                .build();

        // Bỏ qua thời gian miễn sát thương
        if (def.getBoolean("ignore-iframes", false)) {
            target.setNoDamageTicks(0);
        }

        double before = target.getHealth() + target.getAbsorptionAmount();
        target.damage(amount, source);
        if (!target.isValid() || target.isDead()) {
            return true;
        }
        return target.getHealth() + target.getAbsorptionAmount() < before;
    }

    /**
     * Mục tiêu có hợp lệ để vũ khí gây sát thương không.
     * Loại: chính chủ (trừ self-damage), thú thuần hóa của chủ, armor stand, entity bất tử,
     * NPC (metadata "NPC"), người chơi spectator/creative, người chơi ở world tắt PvP.
     */
    public static boolean isValidTarget(Player attacker, Entity target, WeaponDefinition def) {
        if (attacker == null || target == null) {
            return false;
        }
        if (!target.isValid() || target.isDead()) {
            return false;
        }
        if (!(target instanceof LivingEntity)) {
            return false;
        }
        if (target.getUniqueId().equals(attacker.getUniqueId())) {
            return def != null && def.getBoolean("self-damage", false);
        }
        if (target instanceof ArmorStand) {
            return false;
        }
        if (target.isInvulnerable() || target.hasMetadata("NPC")) {
            return false;
        }
        if (target instanceof Player targetPlayer) {
            GameMode mode = targetPlayer.getGameMode();
            if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) {
                return false;
            }
            if (!targetPlayer.getWorld().getPVP()) {
                return false;
            }
        }
        if (target instanceof Tameable tameable && tameable.isTamed()
                && attacker.getUniqueId().equals(tameable.getOwnerUniqueId())) {
            return false;
        }
        return true;
    }
}
