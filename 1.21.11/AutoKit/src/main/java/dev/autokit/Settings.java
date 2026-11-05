package dev.autokit;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

public final class Settings {

    private final AutoKitPlugin plugin;

    public Material boxMaterial = Material.JUKEBOX;
    public long clickCooldownMs = 500;
    public int maxKitsPerPlayer = 10;

    public String ecoType = "VAULT";
    public double sellMultiplier = 5.0;
    public long priceCacheMs = 30_000;
    public double maxTotal = 1.0E12;

    public int itemsPerStep = 1;
    public int stepIntervalTicks = 2;

    public boolean effects = true;
    public String startSound = "block.note_block.bell";
    public String giveSound = "block.note_block.chime";
    public String finishSound = "entity.player.levelup";

    public int priceLines = 10;

    public Settings(AutoKitPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        String mat = c.getString("box.material", "JUKEBOX").toUpperCase(Locale.ROOT);
        boxMaterial = mat.equals("NOTE_BLOCK") ? Material.NOTE_BLOCK : Material.JUKEBOX;
        clickCooldownMs = Math.max(0, c.getLong("box.click-cooldown-ms", 500));
        maxKitsPerPlayer = Math.max(1, c.getInt("kits.max-per-player", 10));

        ecoType = c.getString("pricing.eco-type", "VAULT").toUpperCase(Locale.ROOT);
        sellMultiplier = Math.max(0, c.getDouble("pricing.sell-to-buy-multiplier", 5.0));
        priceCacheMs = Math.max(0, c.getLong("pricing.cache-seconds", 30)) * 1000L;
        maxTotal = c.getDouble("pricing.max-total", 1.0E12);

        itemsPerStep = Math.max(1, c.getInt("give.items-per-step", 1));
        stepIntervalTicks = Math.max(1, c.getInt("give.step-interval-ticks", 2));

        effects = c.getBoolean("effects.enabled", true);
        startSound = c.getString("effects.start-sound", startSound);
        giveSound = c.getString("effects.give-sound", giveSound);
        finishSound = c.getString("effects.finish-sound", finishSound);

        priceLines = Math.max(1, c.getInt("price-table.max-lines", 10));
    }
}
