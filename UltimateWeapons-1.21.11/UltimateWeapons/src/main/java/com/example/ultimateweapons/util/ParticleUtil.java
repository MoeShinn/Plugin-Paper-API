package com.example.ultimateweapons.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * Tiện ích hạt màu (Particle.DUST).
 */
public final class ParticleUtil {

    private static final int MAX_LINE_POINTS = 2048;

    private ParticleUtil() {
    }

    /** "#RRGGBB" -> Color. Sai định dạng thì trả về trắng. */
    public static Color parseHex(String hex) {
        if (hex == null) {
            return Color.WHITE;
        }
        String text = hex.trim();
        if (text.length() != 7 || text.charAt(0) != '#') {
            return Color.WHITE;
        }
        try {
            return Color.fromRGB(Integer.parseInt(text.substring(1), 16));
        } catch (IllegalArgumentException ex) {
            return Color.WHITE;
        }
    }

    /** Một cụm hạt bụi màu tại loc. */
    public static void dust(Location loc, Color color, float size, int count) {
        if (loc == null || color == null || count <= 0) {
            return;
        }
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        float safeSize = Math.max(0.01f, size);
        Particle.DustOptions options = new Particle.DustOptions(color, safeSize);
        world.spawnParticle(Particle.DUST, loc, count, 0.0, 0.0, 0.0, 0.0, options);
    }

    /** Vẽ đường thẳng từ from tới to, mỗi step block đặt một hạt. */
    public static void line(Location from, Location to, Color color, double step) {
        if (from == null || to == null || color == null) {
            return;
        }
        World world = from.getWorld();
        if (world == null || !world.equals(to.getWorld())) {
            return;
        }
        double safeStep = step > 0.0 ? step : 0.5;
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        int segments = (int) Math.min(MAX_LINE_POINTS, Math.max(1.0, Math.ceil(length / safeStep)));
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            Location point = from.clone().add(delta.clone().multiply(t));
            dust(point, color, 1.0f, 1);
        }
    }

    /** Vẽ vòng tròn nằm ngang quanh center. */
    public static void circle(Location center, double radius, int points, Color color) {
        if (center == null || color == null || center.getWorld() == null || radius <= 0.0) {
            return;
        }
        int count = Math.max(3, Math.min(points, MAX_LINE_POINTS));
        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * i / count;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            dust(point, color, 1.0f, 1);
        }
    }
}
