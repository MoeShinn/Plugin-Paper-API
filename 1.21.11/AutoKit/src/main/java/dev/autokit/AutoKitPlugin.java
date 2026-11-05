package dev.autokit;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class AutoKitPlugin extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private ItemKeys keys;
    private KitStorage kitStorage;
    private BoxStorage boxStorage;
    private ShopPriceProvider shop;
    private ItemPricer pricer;
    private GiveService giveService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(this);
        settings.load();
        messages = new Messages(this);
        messages.reload();
        keys = new ItemKeys(
                new NamespacedKey(this, "box"),
                new NamespacedKey(this, "owner"),
                new NamespacedKey(this, "kit"));

        kitStorage = new KitStorage(this);
        boxStorage = new BoxStorage(this);
        boxStorage.load();
        shop = new ShopPriceProvider(this);
        pricer = new ItemPricer(this);
        giveService = new GiveService(this);

        PluginCommand cmd = getCommand("autokit");
        if (cmd != null) {
            AutoKitCommand executor = new AutoKitCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
        getServer().getPluginManager().registerEvents(new BoxListener(this), this);

        // Chay sau khi tat ca plugin da enable xong (ESG + Vault provider)
        getServer().getScheduler().runTask(this, () -> {
            shop.init();
            if (economy() == null) {
                getLogger().warning("Khong tim thay Vault economy provider. Hay cai plugin tien (EssentialsX, CMI, ...).");
            }
        });
        getServer().getScheduler().runTaskTimer(this, () -> boxStorage.saveIfDirty(), 100L, 100L);
    }

    @Override
    public void onDisable() {
        if (giveService != null) giveService.shutdown();
        if (boxStorage != null) boxStorage.shutdown();
    }

    /** Lay Vault Economy moi lan de tranh bi cu khi provider doi. */
    public Economy economy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public void reloadAll() {
        settings.load();
        messages.reload();
        pricer.clearCache();
        shop.init();
    }

    public Settings settings() { return settings; }
    public Messages messages() { return messages; }
    public ItemKeys keys() { return keys; }
    public KitStorage kits() { return kitStorage; }
    public BoxStorage boxes() { return boxStorage; }
    public ShopPriceProvider shop() { return shop; }
    public ItemPricer pricer() { return pricer; }
    public GiveService giveService() { return giveService; }
}
