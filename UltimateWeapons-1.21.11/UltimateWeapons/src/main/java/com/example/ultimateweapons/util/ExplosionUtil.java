package com.example.ultimateweapons.util;

import com.example.ultimateweapons.weapon.WeaponDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Tạo vụ nổ tính cho người dùng (kill credit đúng) và chặn self-damage theo config của vũ khí.
 *
 * Cách hoạt động: createExplosion chạy đồng bộ, nên trong lúc nó chạy ta đánh dấu "nguồn nổ hiện tại".
 * WeaponListener gọi shouldCancel cho mỗi EntityDamageEvent để hủy sát thương không hợp lệ.
 */
public final class ExplosionUtil {

    private record Active(Player source, WeaponDefinition def) {
    }

    // Chỉ khác null trong lúc createExplosion đang chạy (main thread)
    private static Active active = null;

    private ExplosionUtil() {
    }

    public static void explode(Player source, Location loc, float power, boolean fire,
                               boolean breakBlocks, WeaponDefinition def) {
        if (source == null || loc == null || def == null) {
            return;
        }
        World world = loc.getWorld();
        if (world == null || !(power > 0f)) {
            return;
        }
        Active previous = active;
        active = new Active(source, def);
        try {
            world.createExplosion(source, loc, power, fire, breakBlocks);
        } finally {
            active = previous;
        }
    }

    /**
     * Có nên hủy sát thương này không. Chỉ có tác dụng khi đang trong một vụ nổ do explode() tạo.
     * Hủy với sinh vật không hợp lệ (chính chủ khi tắt self-damage, thú thuần hóa của chủ,
     * armor stand, người chơi creative/spectator...). Vật phẩm, xe, v.v. vẫn bị nổ bình thường.
     */
    public static boolean shouldCancel(EntityDamageEvent event) {
        Active current = active;
        if (current == null) {
            return false;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return false;
        }
        Entity victim = event.getEntity();
        if (!(victim instanceof LivingEntity)) {
            return false;
        }
        return !DamageUtil.isValidTarget(current.source(), victim, current.def());
    }
}
