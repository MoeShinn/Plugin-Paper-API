package dev.autokit;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/** items: slot (0-35 tui do, 36-39 giap, 40 tay phu) -> ItemStack. */
public record Kit(String id, UUID owner, Map<Integer, ItemStack> items, long createdAt) {
}
