package dev.autokit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class BoxListener implements Listener {

    private final AutoKitPlugin plugin;

    public BoxListener(AutoKitPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ dat hop nhac

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        ItemUtil.BoxRef ref = ItemUtil.readBox(plugin, e.getItemInHand());
        if (ref == null) return;
        plugin.boxes().put(e.getBlockPlaced(), new BoxData(ref.owner(), ref.kitId()));
        plugin.messages().send(e.getPlayer(), "box.placed");
    }

    // ------------------------------------------------------------------- click

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null || plugin.boxes().isEmpty()) return;

        BoxData data = plugin.boxes().get(block);
        if (data == null) return;

        Material type = block.getType();
        if (type != Material.JUKEBOX && type != Material.NOTE_BLOCK) {
            plugin.boxes().remove(block); // block da bi thay bang cach khac (WorldEdit, /setblock...)
            return;
        }

        e.setCancelled(true); // khong cho nhet dia, doi nota, dat block...
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player player = e.getPlayer();
        plugin.giveService().handleClick(player, data, block, player.isSneaking());
    }

    // ----------------------------------------------------------------- pha hop

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        BoxData data = plugin.boxes().remove(e.getBlock());
        if (data == null) return;

        e.setDropItems(false);
        ItemStack box = ItemUtil.createBox(plugin, data.owner(), data.kitId());
        Location loc = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        e.getBlock().getWorld().dropItemNaturally(loc, box);
    }

    // -------------------------------------------------------- bao ve hop nhac

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (plugin.boxes().isEmpty()) return;
        e.blockList().removeIf(b -> plugin.boxes().contains(b));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (plugin.boxes().isEmpty()) return;
        e.blockList().removeIf(b -> plugin.boxes().contains(b));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (anyBox(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (anyBox(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (plugin.boxes().contains(e.getBlock())) e.setCancelled(true);
    }

    private boolean anyBox(List<Block> blocks) {
        if (plugin.boxes().isEmpty()) return false;
        for (Block b : blocks) if (plugin.boxes().contains(b)) return true;
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.giveService().onQuit(e.getPlayer().getUniqueId());
    }
}
