package dev.autokit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class AutoKitCommand implements TabExecutor {

    private final AutoKitPlugin plugin;
    private final Messages msg;

    public AutoKitCommand(AutoKitPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            msg.send(sender, "usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "save" -> save(sender, args);
            case "give" -> give(sender, args);
            case "delete", "remove" -> delete(sender, args);
            case "list" -> list(sender);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender);
            default -> msg.send(sender, "usage");
        }
        return true;
    }

    // -------------------------------------------------------------------- save

    private void save(CommandSender sender, String[] args) {
        Player p = requireCreator(sender);
        if (p == null) return;
        if (args.length < 2) {
            msg.send(p, "save.usage");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!KitStorage.validId(id)) {
            msg.send(p, "save.invalid-id");
            return;
        }

        UUID owner = p.getUniqueId();
        Kit existing = plugin.kits().get(owner, id);
        boolean overwrite = args.length >= 3 && args[2].equalsIgnoreCase("overwrite");
        if (existing != null && !overwrite) {
            msg.send(p, "save.exists", Placeholder.unparsed("id", id));
            return;
        }
        if (existing == null && !p.hasPermission("autokit.admin")
                && plugin.kits().kitsOf(owner).size() >= plugin.settings().maxKitsPerPlayer) {
            msg.send(p, "save.limit", Placeholder.unparsed("max", String.valueOf(plugin.settings().maxKitsPerPlayer)));
            return;
        }

        PlayerInventory inv = p.getInventory();
        Map<Integer, ItemStack> snapshot = new TreeMap<>();
        for (int slot = 0; slot <= 40; slot++) {
            ItemStack item = inv.getItem(slot);
            if (ItemUtil.isAir(item)) continue;
            if (ItemUtil.readBox(plugin, item) != null) continue; // khong luu hop nhac vao kit
            snapshot.put(slot, item.clone());
        }
        if (snapshot.isEmpty()) {
            msg.send(p, "save.empty");
            return;
        }

        try {
            plugin.kits().save(new Kit(id, owner, snapshot, System.currentTimeMillis()));
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().severe("Loi luu kit " + id + " cua " + p.getName() + ": " + ex);
            msg.send(p, "save.error");
            return;
        }

        if (existing == null) {
            msg.send(p, "save.ok", Placeholder.unparsed("id", id), Placeholder.unparsed("count", String.valueOf(snapshot.size())));
            giveBox(p, owner, id, false);
        } else {
            msg.send(p, "save.ok-overwrite", Placeholder.unparsed("id", id), Placeholder.unparsed("count", String.valueOf(snapshot.size())));
        }
    }

    // -------------------------------------------------------------------- give

    private void give(CommandSender sender, String[] args) {
        Player p = requireCreator(sender);
        if (p == null) return;
        if (args.length < 2) {
            msg.send(p, "give.usage");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        UUID owner = p.getUniqueId();

        if (args.length >= 3) {
            if (!p.hasPermission("autokit.admin")) {
                msg.send(p, "no-permission");
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[2]);
            if (target == null) {
                msg.send(p, "give.player-not-found");
                return;
            }
            owner = target.getUniqueId();
        }

        if (plugin.kits().get(owner, id) == null) {
            msg.send(p, "give.not-found", Placeholder.unparsed("id", id));
            return;
        }
        giveBox(p, owner, id, true);
    }

    private void giveBox(Player p, UUID owner, String id, boolean announce) {
        ItemStack box = ItemUtil.createBox(plugin, owner, id);
        Map<Integer, ItemStack> left = p.getInventory().addItem(box);
        if (!left.isEmpty()) {
            for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
            msg.send(p, "give.inv-full");
        } else if (announce) {
            msg.send(p, "give.ok", Placeholder.unparsed("id", id));
        }
    }

    // ------------------------------------------------------------------ delete

    private void delete(CommandSender sender, String[] args) {
        Player p = requireCreator(sender);
        if (p == null) return;
        if (args.length < 2) {
            msg.send(p, "delete.usage");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        try {
            if (plugin.kits().delete(p.getUniqueId(), id)) {
                msg.send(p, "delete.ok", Placeholder.unparsed("id", id));
            } else {
                msg.send(p, "delete.not-found", Placeholder.unparsed("id", id));
            }
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().severe("Loi xoa kit " + id + ": " + ex);
            msg.send(p, "delete.error");
        }
    }

    // -------------------------------------------------------------------- list

    private void list(CommandSender sender) {
        Player p = requireCreator(sender);
        if (p == null) return;
        Map<String, Kit> kits = plugin.kits().kitsOf(p.getUniqueId());
        if (kits.isEmpty()) {
            msg.send(p, "list.empty");
            return;
        }
        msg.send(p, "list.header", Placeholder.unparsed("count", String.valueOf(kits.size())));
        for (Kit kit : kits.values()) {
            msg.send(p, "list.line",
                    Placeholder.unparsed("id", kit.id()),
                    Placeholder.unparsed("count", String.valueOf(kit.items().size())));
        }
    }

    // ------------------------------------------------------------ reload/debug

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("autokit.admin")) {
            msg.send(sender, "no-permission");
            return;
        }
        plugin.reloadAll();
        msg.send(sender, "reload.ok");
    }

    private void debug(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "player-only");
            return;
        }
        if (!p.hasPermission("autokit.admin")) {
            msg.send(p, "no-permission");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (ItemUtil.isAir(hand)) {
            msg.send(p, "debug.hold-item");
            return;
        }
        if (!plugin.shop().isReady()) {
            msg.send(p, "click.shop-unavailable");
            return;
        }
        List<String> trace = new ArrayList<>();
        double total;
        try {
            total = plugin.pricer().price(ItemUtil.normalizeDurability(hand, 0), trace);
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("debug loi: " + ex);
            msg.send(p, "click.price-error");
            return;
        }
        msg.send(p, "debug.header");
        for (String line : trace) {
            p.sendMessage(msg.get("debug.line", Placeholder.unparsed("line", line)));
        }
        msg.send(p, "debug.total", Placeholder.component("price", plugin.giveService().money(total)));
    }

    // ------------------------------------------------------------------ helpers

    private Player requireCreator(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "player-only");
            return null;
        }
        if (!p.hasPermission("autokit.create")) {
            msg.send(p, "no-permission");
            return null;
        }
        return p;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("autokit.create")) out.addAll(List.of("save", "give", "delete", "list"));
            if (sender.hasPermission("autokit.admin")) out.addAll(List.of("reload", "debug"));
        } else if (args.length == 2 && sender instanceof Player p && sender.hasPermission("autokit.create")) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give") || sub.equals("delete") || sub.equals("save")) {
                out.addAll(plugin.kits().kitsOf(p.getUniqueId()).keySet());
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("save")) out.add("overwrite");
            if (sub.equals("give") && sender.hasPermission("autokit.admin")) {
                for (Player online : Bukkit.getOnlinePlayers()) out.add(online.getName());
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
