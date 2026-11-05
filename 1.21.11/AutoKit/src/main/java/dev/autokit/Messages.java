package dev.autokit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Messages {

    private final AutoKitPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration yml = new YamlConfiguration();

    public Messages(AutoKitPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        yml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                yml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
        }
    }

    private TagResolver resolver(TagResolver... extra) {
        return TagResolver.builder()
                .resolver(Placeholder.parsed("prefix", yml.getString("prefix", "")))
                .resolvers(extra)
                .build();
    }

    public Component get(String key, TagResolver... extra) {
        String raw = yml.getString(key);
        if (raw == null) return Component.text("[missing message: " + key + "]");
        return mm.deserialize(raw, resolver(extra));
    }

    public List<Component> list(String key, TagResolver... extra) {
        List<Component> out = new ArrayList<>();
        for (String raw : yml.getStringList(key)) out.add(mm.deserialize(raw, resolver(extra)));
        return out;
    }

    public void send(CommandSender to, String key, TagResolver... extra) {
        to.sendMessage(get(key, extra));
    }
}
