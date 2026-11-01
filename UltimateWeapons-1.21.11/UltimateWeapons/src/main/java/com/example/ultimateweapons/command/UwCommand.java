package com.example.ultimateweapons.command;

import com.example.ultimateweapons.UltimateWeaponsPlugin;
import com.example.ultimateweapons.addon.AddonManager;
import com.example.ultimateweapons.weapon.UltimateWeapon;
import com.example.ultimateweapons.weapon.WeaponDefinition;
import com.example.ultimateweapons.weapon.WeaponRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lệnh /uw: give, list, addons, reload. Quyền uw.admin. Mọi message lấy từ config.yml.
 */
public final class UwCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "uw.admin";
    private static final List<String> SUBCOMMANDS = List.of("give", "list", "addons", "reload");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final UltimateWeaponsPlugin plugin;

    public UwCommand(UltimateWeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            plugin.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.send(sender, "usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "list" -> list(sender);
            case "addons" -> addons(sender);
            case "reload" -> reload(sender);
            default -> plugin.send(sender, "usage");
        }
        return true;
    }

    // ------------------------------------------------------------------
    // /uw give <id> [player]
    // ------------------------------------------------------------------

    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.send(sender, "usage-give");
            return;
        }
        WeaponRegistry registry = plugin.getWeaponRegistry();
        UltimateWeapon weapon = registry.get(args[1]);
        WeaponDefinition def = registry.getDefinition(args[1]);
        if (weapon == null || def == null) {
            plugin.send(sender, "unknown-weapon", Placeholder.unparsed("id", args[1]));
            return;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.send(sender, "player-not-found", Placeholder.unparsed("player", args[2]));
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            plugin.send(sender, "console-needs-player");
            return;
        }

        ItemStack item = weapon.createItem();
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack extra : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), extra);
            }
            plugin.send(sender, "inventory-full", Placeholder.unparsed("player", target.getName()));
        }

        Component weaponName = def.displayName();
        plugin.send(target, "received", Placeholder.component("weapon", weaponName));
        if (!sender.equals(target)) {
            plugin.send(sender, "given",
                    Placeholder.component("weapon", weaponName),
                    Placeholder.unparsed("player", target.getName()));
        }
    }

    // ------------------------------------------------------------------
    // /uw list: mỗi vũ khí 2 dòng (id có thể click, rồi "Tên | mô tả [nguồn]")
    // ------------------------------------------------------------------

    private void list(CommandSender sender) {
        WeaponRegistry registry = plugin.getWeaponRegistry();
        Collection<WeaponDefinition> definitions = registry.definitions();
        if (definitions.isEmpty()) {
            plugin.send(sender, "list-empty");
            return;
        }
        plugin.send(sender, "list-header", Placeholder.unparsed("count", String.valueOf(definitions.size())));
        for (WeaponDefinition def : definitions) {
            Component idLine = plugin.rawMessage("list-id", Placeholder.unparsed("id", def.id()))
                    .clickEvent(ClickEvent.runCommand("/uw give " + def.id()))
                    .hoverEvent(HoverEvent.showText(plugin.rawMessage("list-hover")));
            sender.sendMessage(idLine);

            String source = registry.getSource(def.id());
            Component infoLine = plugin.rawMessage("list-info",
                    Placeholder.component("name", def.displayName()),
                    Placeholder.component("description", MM.deserialize(def.description())),
                    Placeholder.unparsed("source", source == null ? "?" : source));
            sender.sendMessage(infoLine);
        }
    }

    // ------------------------------------------------------------------
    // /uw addons
    // ------------------------------------------------------------------

    private void addons(CommandSender sender) {
        AddonManager manager = plugin.getAddonManager();
        List<AddonManager.AddonInfo> infos = manager.getLoadedAddons();
        if (infos.isEmpty()) {
            plugin.send(sender, "addons-empty");
        } else {
            plugin.send(sender, "addons-header", Placeholder.unparsed("count", String.valueOf(infos.size())));
            for (AddonManager.AddonInfo info : infos) {
                sender.sendMessage(plugin.rawMessage("addon-line",
                        Placeholder.unparsed("name", info.name()),
                        Placeholder.unparsed("version", info.version()),
                        Placeholder.unparsed("types", String.join(", ", info.types())),
                        Placeholder.unparsed("jar", info.jarName())));
            }
        }
        sendAddonErrors(sender, manager.getLastErrors());
    }

    private void sendAddonErrors(CommandSender sender, List<AddonManager.AddonError> errors) {
        if (errors.isEmpty()) {
            return;
        }
        plugin.send(sender, "addon-errors-header", Placeholder.unparsed("count", String.valueOf(errors.size())));
        for (AddonManager.AddonError error : errors) {
            sender.sendMessage(plugin.rawMessage("addon-error-line",
                    Placeholder.unparsed("source", error.source()),
                    Placeholder.unparsed("reason", error.reason())));
        }
    }

    // ------------------------------------------------------------------
    // /uw reload
    // ------------------------------------------------------------------

    private void reload(CommandSender sender) {
        UltimateWeaponsPlugin.ReloadResult result = plugin.reloadAll();
        plugin.send(sender, "reload-success",
                Placeholder.unparsed("weapons", String.valueOf(result.weaponCount())),
                Placeholder.unparsed("addons", String.valueOf(result.addonCount())));
        sendAddonErrors(sender, result.addonErrors());
        if (!result.weaponErrors().isEmpty()) {
            plugin.send(sender, "reload-errors-header",
                    Placeholder.unparsed("count", String.valueOf(result.weaponErrors().size())));
            for (WeaponRegistry.LoadError error : result.weaponErrors()) {
                sender.sendMessage(plugin.rawMessage("reload-error-line",
                        Placeholder.unparsed("file", error.file()),
                        Placeholder.unparsed("reason", error.reason())));
            }
        }
    }

    // ------------------------------------------------------------------
    // Tab complete
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> ids = new ArrayList<>();
            for (UltimateWeapon weapon : plugin.getWeaponRegistry().all()) {
                ids.add(weapon.id());
            }
            return filter(ids, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
