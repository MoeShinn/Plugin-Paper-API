package dev.autokit;

import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Luu vi tri hop nhac da dat vao plugins/AutoKit/boxes.yml (file rieng, ghi async + atomic). */
public final class BoxStorage {

    private final AutoKitPlugin plugin;
    private final Path file;
    private final Map<BoxKey, BoxData> boxes = new HashMap<>();
    private boolean dirty;

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AutoKit-IO");
        t.setDaemon(true);
        return t;
    });

    public BoxStorage(AutoKitPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("boxes.yml");
    }

    public void load() {
        if (!file.toFile().exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file.toFile());
        for (Map<?, ?> m : yml.getMapList("boxes")) {
            try {
                UUID world = UUID.fromString(String.valueOf(m.get("world")));
                int x = ((Number) m.get("x")).intValue();
                int y = ((Number) m.get("y")).intValue();
                int z = ((Number) m.get("z")).intValue();
                UUID owner = UUID.fromString(String.valueOf(m.get("owner")));
                String kit = String.valueOf(m.get("kit"));
                boxes.put(new BoxKey(world, x, y, z), new BoxData(owner, kit));
            } catch (Exception ex) {
                plugin.getLogger().warning("Bo qua 1 hop nhac loi trong boxes.yml: " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Da nap " + boxes.size() + " hop nhac.");
    }

    public boolean isEmpty() {
        return boxes.isEmpty();
    }

    public BoxData get(Block block) {
        return boxes.get(BoxKey.of(block));
    }

    public boolean contains(Block block) {
        return !boxes.isEmpty() && boxes.containsKey(BoxKey.of(block));
    }

    public void put(Block block, BoxData data) {
        boxes.put(BoxKey.of(block), data);
        dirty = true;
    }

    public BoxData remove(Block block) {
        BoxData removed = boxes.remove(BoxKey.of(block));
        if (removed != null) dirty = true;
        return removed;
    }

    private String build() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<BoxKey, BoxData> e : boxes.entrySet()) {
            BoxKey k = e.getKey();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", k.world().toString());
            m.put("x", k.x());
            m.put("y", k.y());
            m.put("z", k.z());
            m.put("owner", e.getValue().owner().toString());
            m.put("kit", e.getValue().kitId());
            list.add(m);
        }
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("boxes", list);
        return yml.saveToString();
    }

    public void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        String data = build();
        io.execute(() -> write(data));
    }

    private void write(String data) {
        try {
            FileUtil.writeAtomic(file, data, true);
        } catch (Exception ex) {
            plugin.getLogger().severe("Khong ghi duoc boxes.yml: " + ex.getMessage());
        }
    }

    public void shutdown() {
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (dirty) {
            dirty = false;
            write(build());
        }
    }
}
