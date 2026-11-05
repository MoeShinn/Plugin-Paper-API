package dev.autokit;

import org.bukkit.block.Block;

import java.util.UUID;

public record BoxKey(UUID world, int x, int y, int z) {
    public static BoxKey of(Block block) {
        return new BoxKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
