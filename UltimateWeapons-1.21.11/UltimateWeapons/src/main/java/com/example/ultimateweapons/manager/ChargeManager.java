package com.example.ultimateweapons.manager;

import com.example.ultimateweapons.UltimateWeaponsPlugin;
import com.example.ultimateweapons.weapon.WeaponDefinition;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Quản lý việc sạc chiêu kèm thanh BossBar.
 * Thanh đỏ khi chưa đủ mức tối thiểu, xanh khi đủ, vàng khi đầy.
 * Tự hủy khi người chơi thoát/chết, đổi slot cầm, không còn cầm đúng vũ khí ở tay chính.
 * Giả định vũ khí sạc bằng TAY CHÍNH.
 */
public final class ChargeManager {

    private static final class State {
        final WeaponDefinition def;
        final double minSeconds;
        final double maxSeconds;
        final long startNanos;
        final int heldSlot;
        final BossBar bar;

        State(WeaponDefinition def, double minSeconds, double maxSeconds, int heldSlot, BossBar bar) {
            this.def = def;
            this.minSeconds = minSeconds;
            this.maxSeconds = maxSeconds;
            this.startNanos = System.nanoTime();
            this.heldSlot = heldSlot;
            this.bar = bar;
        }

        double elapsedSeconds() {
            return (System.nanoTime() - startNanos) / 1_000_000_000.0;
        }
    }

    private final UltimateWeaponsPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private BukkitTask task;

    public ChargeManager(UltimateWeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Bắt đầu sạc và hiện BossBar. Nếu đang sạc thì hủy lần cũ. */
    public void start(Player p, WeaponDefinition def, double minSeconds, double maxSeconds) {
        if (p == null || !p.isOnline() || def == null) {
            return;
        }
        cancel(p);
        double min = Math.max(0.0, minSeconds);
        double max = Math.max(Math.max(min, 0.05), maxSeconds);

        BossBar bar = BossBar.bossBar(def.displayName(), 0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        State state = new State(def, min, max, p.getInventory().getHeldItemSlot(), bar);
        states.put(p.getUniqueId(), state);
        p.showBossBar(bar);
        refreshBar(state);
        ensureTask();
    }

    public boolean isCharging(Player p) {
        return p != null && states.containsKey(p.getUniqueId());
    }

    /** Số giây đã sạc, giới hạn bởi maxSeconds. 0 nếu không đang sạc. */
    public double getChargeSeconds(Player p) {
        State state = p == null ? null : states.get(p.getUniqueId());
        if (state == null) {
            return 0.0;
        }
        return Math.min(state.elapsedSeconds(), state.maxSeconds);
    }

    public boolean hasReachedMinimum(Player p) {
        State state = p == null ? null : states.get(p.getUniqueId());
        return state != null && state.elapsedSeconds() >= state.minSeconds;
    }

    /** Kết thúc sạc, dọn thanh sạc và trả về số giây đã sạc (0 nếu không đang sạc). */
    public double stop(Player p) {
        if (p == null) {
            return 0.0;
        }
        State state = states.remove(p.getUniqueId());
        if (state == null) {
            return 0.0;
        }
        double seconds = Math.min(state.elapsedSeconds(), state.maxSeconds);
        p.hideBossBar(state.bar);
        stopTaskIfIdle();
        return seconds;
    }

    /** Hủy sạc và dọn thanh sạc. */
    public void cancel(Player p) {
        if (p == null) {
            return;
        }
        State state = states.remove(p.getUniqueId());
        if (state != null) {
            p.hideBossBar(state.bar);
        }
        stopTaskIfIdle();
    }

    /** Hủy mọi lượt sạc (reload/disable). */
    public void cancelAll() {
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.hideBossBar(entry.getValue().bar);
            }
        }
        states.clear();
        stopTaskIfIdle();
    }

    // ------------------------------------------------------------------

    private void ensureTask() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void stopTaskIfIdle() {
        if (states.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        Iterator<Map.Entry<UUID, State>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> entry = it.next();
            State state = entry.getValue();
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline() || p.isDead() || !stillHolding(p, state)) {
                if (p != null) {
                    p.hideBossBar(state.bar);
                }
                it.remove();
                continue;
            }
            refreshBar(state);
        }
        stopTaskIfIdle();
    }

    /** Còn đúng slot cầm và đúng vũ khí ở tay chính không. */
    private boolean stillHolding(Player p, State state) {
        if (p.getInventory().getHeldItemSlot() != state.heldSlot) {
            return false;
        }
        String id = plugin.getItemKeys().readWeaponId(p.getInventory().getItemInMainHand());
        return state.def.id().equals(id);
    }

    private void refreshBar(State state) {
        double elapsed = state.elapsedSeconds();
        double shown = Math.min(elapsed, state.maxSeconds);
        float progress = (float) Math.max(0.0, Math.min(1.0, shown / state.maxSeconds));

        BossBar.Color color;
        if (elapsed >= state.maxSeconds) {
            color = BossBar.Color.YELLOW;
        } else if (elapsed >= state.minSeconds) {
            color = BossBar.Color.GREEN;
        } else {
            color = BossBar.Color.RED;
        }

        String text = String.format(Locale.ROOT, "  %.1fs / %.1fs", shown, state.maxSeconds);
        state.bar.name(state.def.displayName().append(Component.text(text, NamedTextColor.WHITE)));
        state.bar.progress(progress);
        state.bar.color(color);
    }
}
