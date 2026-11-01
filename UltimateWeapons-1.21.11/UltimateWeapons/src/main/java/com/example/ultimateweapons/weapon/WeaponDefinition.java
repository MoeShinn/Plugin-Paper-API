package com.example.ultimateweapons.weapon;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Định nghĩa một vũ khí, dựng từ một file weapons/*.yml.
 *
 * QUY ƯỚC BẮT BUỘC cho code vũ khí:
 *  - Luôn đọc chỉ số qua def.getDouble("damage", 7.0) (hoặc def.config().getDouble(...))
 *    TẠI THỜI ĐIỂM DÙNG. KHÔNG cache chỉ số vào field, để /uw reload có hiệu lực ngay.
 *  - KHÔNG lưu chỉ số vào ItemStack. Item chỉ chứa PDC "weapon_id".
 *  - Các khóa chung: cooldown-seconds, self-damage, ignore-iframes, ignore-armor,
 *    custom-model-data, unbreakable, lore, hide-attributes.
 */
public final class WeaponDefinition {

    private final String id;
    private final String type;
    private final Component displayName;
    private final String description;
    private final Material material;
    private final ConfigurationSection config;

    public WeaponDefinition(String id, String type, Component displayName, String description,
                            Material material, ConfigurationSection config) {
        this.id = id;
        this.type = type;
        this.displayName = displayName;
        this.description = description;
        this.material = material;
        this.config = config;
    }

    public String id() {
        return id;
    }

    public String type() {
        return type;
    }

    /** Tên hiển thị đã parse từ MiniMessage. */
    public Component displayName() {
        return displayName;
    }

    /** Chuỗi MiniMessage thô. */
    public String description() {
        return description;
    }

    public Material material() {
        return material;
    }

    /** Toàn bộ nội dung yml của vũ khí. */
    public ConfigurationSection config() {
        return config;
    }

    public double getDouble(String path, double fallback) {
        return config.getDouble(path, fallback);
    }

    public int getInt(String path, int fallback) {
        return config.getInt(path, fallback);
    }

    public boolean getBoolean(String path, boolean fallback) {
        return config.getBoolean(path, fallback);
    }

    public String getString(String path, String fallback) {
        return config.getString(path, fallback);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }
}
