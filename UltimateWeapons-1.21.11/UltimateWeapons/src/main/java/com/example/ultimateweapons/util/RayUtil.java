package com.example.ultimateweapons.util;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Tiện ích tia / hộp quét. Không bao giờ ném exception với tham số không hợp lệ (trả về rỗng / điểm đầu).
 */
public final class RayUtil {

    private record Hit(LivingEntity entity, double distanceSquared) {
    }

    private RayUtil() {
    }

    /**
     * Các sinh vật nằm trong "hộp" quanh đường thẳng, sắp theo khoảng cách tăng dần.
     * halfWidth: nửa bề rộng ngang. halfHeight: nửa chiều cao dọc (nhát chém cao nhiều block dùng giá trị lớn).
     * Cách làm: nới hộp của từng entity ra (halfWidth, halfHeight, halfWidth) rồi bắn tia tâm vào hộp đó.
     * filter có thể null (chấp nhận tất cả).
     */
    public static List<LivingEntity> entitiesAlongRay(Location start, Vector direction, double range,
                                                      double halfWidth, double halfHeight,
                                                      Predicate<Entity> filter) {
        List<LivingEntity> result = new ArrayList<>();
        if (start == null || direction == null || range <= 0.0 || !Double.isFinite(range)) {
            return result;
        }
        World world = start.getWorld();
        if (world == null) {
            return result;
        }
        Vector dir = direction.clone();
        if (dir.lengthSquared() < 1.0E-8) {
            return result;
        }
        dir.normalize();

        double hw = Math.max(0.0, halfWidth);
        double hh = Math.max(0.0, halfHeight);
        Vector origin = start.toVector();
        Vector end = origin.clone().add(dir.clone().multiply(range));

        // Vùng tìm kiếm thô, dư 1 block để bắt cả entity có hộp lớn
        BoundingBox search = BoundingBox.of(origin, end).expand(hw + 1.0, hh + 1.0, hw + 1.0);

        List<Hit> hits = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(search)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.isDead() || !living.isValid()) {
                continue;
            }
            if (filter != null && !filter.test(living)) {
                continue;
            }
            BoundingBox box = living.getBoundingBox().expand(hw, hh, hw);
            RayTraceResult hit = box.rayTrace(origin, dir, range);
            if (hit == null) {
                continue;
            }
            hits.add(new Hit(living, hit.getHitPosition().distanceSquared(origin)));
        }
        hits.sort(Comparator.comparingDouble(Hit::distanceSquared));
        for (Hit hit : hits) {
            result.add(hit.entity());
        }
        return result;
    }

    /**
     * Điểm cuối của tia. Nếu stopAtBlock thì dừng tại block đặc đầu tiên trúng phải.
     */
    public static Location rayEnd(Location start, Vector direction, double range, boolean stopAtBlock) {
        if (start == null) {
            return null;
        }
        World world = start.getWorld();
        if (world == null || direction == null || range <= 0.0 || !Double.isFinite(range)
                || direction.lengthSquared() < 1.0E-8) {
            return start.clone();
        }
        Vector dir = direction.clone().normalize();
        if (stopAtBlock) {
            RayTraceResult result = world.rayTraceBlocks(start, dir, range, FluidCollisionMode.NEVER, true);
            if (result != null) {
                return result.getHitPosition().toLocation(world, start.getYaw(), start.getPitch());
            }
        }
        return start.clone().add(dir.multiply(range));
    }
}
