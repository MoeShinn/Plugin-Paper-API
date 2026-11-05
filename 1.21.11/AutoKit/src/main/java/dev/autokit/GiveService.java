package dev.autokit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Xu ly click hop nhac: tinh gia, xem gia, tru tien, give item dan theo tick. */
public final class GiveService {

    public record Entry(int slot, ItemStack item, double price) {
    }

    public record Plan(List<Entry> entries, int skipped, double total) {
    }

    private final AutoKitPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public GiveService(AutoKitPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ click

    public void handleClick(Player player, BoxData box, Block block, boolean preview) {
        Messages msg = plugin.messages();

        if (!player.hasPermission("autokit.use")) {
            msg.send(player, "no-permission");
            return;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            msg.send(player, "click.busy");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < plugin.settings().clickCooldownMs) return;
        cooldowns.put(player.getUniqueId(), now);

        if (!plugin.shop().isReady()) {
            msg.send(player, "click.shop-unavailable");
            return;
        }
        Kit kit = plugin.kits().get(box.owner(), box.kitId());
        if (kit == null) {
            msg.send(player, "click.kit-missing", Placeholder.unparsed("id", box.kitId()));
            return;
        }

        Plan plan;
        try {
            plan = buildPlan(player, kit);
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Loi tinh gia kit " + kit.id() + " cho " + player.getName() + ": " + ex);
            msg.send(player, "click.price-error");
            return;
        }

        if (plan.entries().isEmpty()) {
            msg.send(player, "click.nothing");
            return;
        }
        if (plan.total() > plugin.settings().maxTotal) {
            msg.send(player, "click.too-expensive");
            return;
        }

        if (preview) {
            sendPriceTable(player, kit, plan);
        } else {
            purchase(player, kit, plan, block.getLocation().add(0.5, 1.1, 0.5));
        }
    }

    public void onQuit(UUID id) {
        cooldowns.remove(id);
    }

    // ------------------------------------------------------------------- plan

    public Plan buildPlan(Player player, Kit kit) {
        PlayerInventory inv = player.getInventory();
        List<Entry> entries = new ArrayList<>();
        int skipped = 0;
        double total = 0;

        for (Map.Entry<Integer, ItemStack> e : kit.items().entrySet()) {
            int slot = e.getKey();
            if (slot < 0 || slot > 40) continue;
            if (!ItemUtil.isAir(inv.getItem(slot))) {
                skipped++; // slot da co do -> bo qua, khong tinh tien
                continue;
            }
            ItemStack give = ItemUtil.normalizeDurability(e.getValue(), 0);
            double price = plugin.pricer().price(give);
            if (!Double.isFinite(price) || price < 0) {
                throw new IllegalStateException("Gia khong hop le (" + price + ") cho " + give.getType());
            }
            entries.add(new Entry(slot, give, price));
            total += price;
        }
        return new Plan(entries, skipped, total);
    }

    private void sendPriceTable(Player p, Kit kit, Plan plan) {
        Messages msg = plugin.messages();
        Economy eco = plugin.economy();

        msg.send(p, "price.header", Placeholder.unparsed("id", kit.id()));
        msg.send(p, "price.summary",
                Placeholder.unparsed("count", String.valueOf(plan.entries().size())),
                Placeholder.unparsed("skipped", String.valueOf(plan.skipped())));

        List<Entry> priced = new ArrayList<>();
        for (Entry e : plan.entries()) if (e.price() > 0) priced.add(e);
        priced.sort((a, b) -> Double.compare(b.price(), a.price()));

        int max = plugin.settings().priceLines;
        int shown = 0;
        for (Entry e : priced) {
            if (shown >= max) break;
            msg.send(p, "price.line",
                    Placeholder.unparsed("amount", String.valueOf(e.item().getAmount())),
                    Placeholder.component("item", itemName(e.item())),
                    Placeholder.component("price", money(e.price())));
            shown++;
        }
        if (priced.size() > shown) {
            msg.send(p, "price.more", Placeholder.unparsed("more", String.valueOf(priced.size() - shown)));
        }
        int free = plan.entries().size() - priced.size();
        if (free > 0) msg.send(p, "price.free", Placeholder.unparsed("free", String.valueOf(free)));

        Component balance = eco == null ? Component.text("?") : money(eco.getBalance(p));
        msg.send(p, "price.total",
                Placeholder.component("total", money(plan.total())),
                Placeholder.component("balance", balance));
        msg.send(p, "price.hint");
    }

    // --------------------------------------------------------------- purchase

    private void purchase(Player player, Kit kit, Plan plan, Location boxLoc) {
        Messages msg = plugin.messages();
        double charge = round2(plan.total());

        if (charge > 0) {
            Economy eco = plugin.economy();
            if (eco == null) {
                msg.send(player, "click.no-economy");
                return;
            }
            if (!eco.has(player, charge)) {
                msg.send(player, "click.not-enough",
                        Placeholder.component("price", money(charge)),
                        Placeholder.component("balance", money(eco.getBalance(player))));
                return;
            }
            EconomyResponse r = eco.withdrawPlayer(player, charge);
            if (!r.transactionSuccess()) {
                msg.send(player, "click.withdraw-failed");
                return;
            }
        }

        if (charge > 0) {
            msg.send(player, "click.start",
                    Placeholder.unparsed("id", kit.id()),
                    Placeholder.unparsed("count", String.valueOf(plan.entries().size())),
                    Placeholder.component("price", money(charge)));
        } else {
            msg.send(player, "click.start-free",
                    Placeholder.unparsed("id", kit.id()),
                    Placeholder.unparsed("count", String.valueOf(plan.entries().size())));
        }

        Settings s = plugin.settings();
        if (s.effects) {
            player.playSound(player.getLocation(), s.startSound, 0.8f, 1.0f);
            player.spawnParticle(Particle.NOTE, boxLoc, 6, 0.3, 0.2, 0.3, 1.0);
        }

        Session session = new Session(player, plan, charge, boxLoc);
        sessions.put(player.getUniqueId(), session);
        session.runTaskTimer(plugin, 1L, s.stepIntervalTicks);
    }

    /** Huy het phien dang chay (tat server) va hoan tien phan chua give. */
    public void shutdown() {
        for (Session session : new ArrayList<>(sessions.values())) session.abort(false);
        sessions.clear();
    }

    private final class Session extends BukkitRunnable {
        private final Player player;
        private final UUID id;
        private final Deque<Entry> queue;
        private final int total;
        private final double charged;
        private final Location boxLoc;
        private int given;
        private int lateSkipped;
        private double refund;

        Session(Player player, Plan plan, double charged, Location boxLoc) {
            this.player = player;
            this.id = player.getUniqueId();
            this.queue = new ArrayDeque<>(plan.entries());
            this.total = plan.entries().size();
            this.charged = charged;
            this.boxLoc = boxLoc;
        }

        @Override
        public void run() {
            if (!player.isOnline() || player.isDead()) {
                abort(true);
                return;
            }
            Settings s = plugin.settings();
            PlayerInventory inv = player.getInventory();

            for (int i = 0; i < s.itemsPerStep && !queue.isEmpty(); i++) {
                Entry e = queue.poll();
                if (!ItemUtil.isAir(inv.getItem(e.slot()))) {
                    // Trong luc dang give, o nay bi chiem -> hoan tien rieng o nay
                    refund += e.price();
                    lateSkipped++;
                    continue;
                }
                inv.setItem(e.slot(), e.item().clone());
                given++;
                if (s.effects) {
                    float pitch = 0.6f + 1.4f * ((float) given / Math.max(1, total));
                    player.playSound(player.getLocation(), s.giveSound, 0.6f, pitch);
                    player.spawnParticle(Particle.NOTE, boxLoc, 1, 0.3, 0.2, 0.3, 1.0);
                    player.spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 6, 0.4, 0.6, 0.4, 0.4);
                }
            }

            player.sendActionBar(plugin.messages().get("click.progress",
                    Placeholder.unparsed("given", String.valueOf(given)),
                    Placeholder.unparsed("total", String.valueOf(total))));

            if (queue.isEmpty()) finish();
        }

        private void finish() {
            cancel();
            sessions.remove(id);
            Messages msg = plugin.messages();
            Settings s = plugin.settings();

            if (refund > 0) deposit(id, round2(refund));
            if (s.effects) {
                player.playSound(player.getLocation(), s.finishSound, 0.8f, 1.0f);
                player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 12, 0.5, 0.7, 0.5, 0.0);
            }
            msg.send(player, "click.done", Placeholder.unparsed("given", String.valueOf(given)));
            if (refund > 0) {
                msg.send(player, "click.done-refund",
                        Placeholder.component("price", money(round2(refund))),
                        Placeholder.unparsed("skipped", String.valueOf(lateSkipped)));
            }
        }

        /** Dung giua chung: hoan lai toan bo phan chua give. */
        void abort(boolean notify) {
            if (!isCancelled()) cancel();
            sessions.remove(id);
            double remaining = refund;
            for (Entry e : queue) remaining += e.price();
            queue.clear();
            double amount = round2(remaining);
            if (amount > 0) deposit(id, amount);
            if (notify && player.isOnline()) {
                plugin.messages().send(player, "click.aborted", Placeholder.component("price", money(amount)));
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private void deposit(UUID id, double amount) {
        if (amount <= 0) return;
        Economy eco = plugin.economy();
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        if (eco == null) {
            plugin.getLogger().severe("KHONG HOAN DUOC " + amount + " cho " + id + ": khong co economy provider!");
            return;
        }
        EconomyResponse r = eco.depositPlayer(op, amount);
        if (!r.transactionSuccess()) {
            plugin.getLogger().severe("KHONG HOAN DUOC " + amount + " cho " + id + ": " + r.errorMessage);
        }
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public Component money(double value) {
        Economy eco = plugin.economy();
        String text = eco != null ? eco.format(value) : String.format(Locale.US, "%,.2f", value);
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    private static Component itemName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.displayName();
        return Component.translatable(item.getType().translationKey());
    }
}
