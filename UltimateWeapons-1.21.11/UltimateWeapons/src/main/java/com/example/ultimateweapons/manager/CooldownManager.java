package com.example.ultimateweapons.manager;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Quản lý hồi chiêu theo (người chơi, id vũ khí). Chỉ dùng trên main thread.
 * Lõi KHÔNG tự áp dụng cooldown: vũ khí tự gọi các hàm này.
 */
public final class CooldownManager {

    // uuid -> (weaponId -> thời điểm hết hạn tính bằng millis)
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public boolean isOnCooldown(Player p, String weaponId) {
        return remainingMillis(p, weaponId) > 0;
    }

    /** Đặt hồi chiêu. millis <= 0 nghĩa là xóa hồi chiêu của vũ khí đó. */
    public void set(Player p, String weaponId, long millis) {
        if (p == null || weaponId == null) {
            return;
        }
        UUID uuid = p.getUniqueId();
        if (millis <= 0) {
            Map<String, Long> map = cooldowns.get(uuid);
            if (map != null) {
                map.remove(weaponId);
                if (map.isEmpty()) {
                    cooldowns.remove(uuid);
                }
            }
            return;
        }
        cooldowns.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(weaponId, System.currentTimeMillis() + millis);
    }

    /** Số millis còn lại, 0 nếu không có hồi chiêu. */
    public long remainingMillis(Player p, String weaponId) {
        if (p == null || weaponId == null) {
            return 0L;
        }
        UUID uuid = p.getUniqueId();
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) {
            return 0L;
        }
        Long expiry = map.get(weaponId);
        if (expiry == null) {
            return 0L;
        }
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            map.remove(weaponId);
            if (map.isEmpty()) {
                cooldowns.remove(uuid);
            }
            return 0L;
        }
        return remaining;
    }

    /** Xóa toàn bộ hồi chiêu của một người chơi. */
    public void clear(Player p) {
        if (p != null) {
            cooldowns.remove(p.getUniqueId());
        }
    }

    /** Xóa hồi chiêu của mọi người (dùng khi disable). */
    public void clearAll() {
        cooldowns.clear();
    }

    /** Chỉ bỏ các hồi chiêu đã hết hạn của một người (dùng khi họ thoát). */
    public void purgeExpired(UUID uuid) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) {
            return;
        }
        long now = System.currentTimeMillis();
        map.values().removeIf(expiry -> expiry <= now);
        if (map.isEmpty()) {
            cooldowns.remove(uuid);
        }
    }

    /** Dọn mọi hồi chiêu đã hết hạn của tất cả người chơi (gọi định kỳ). */
    public void purgeAllExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Map<String, Long>>> it = cooldowns.entrySet().iterator();
        while (it.hasNext()) {
            Map<String, Long> map = it.next().getValue();
            map.values().removeIf(expiry -> expiry <= now);
            if (map.isEmpty()) {
                it.remove();
            }
        }
    }
}
