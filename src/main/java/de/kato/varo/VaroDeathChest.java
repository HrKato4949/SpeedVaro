package de.kato.varo;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Display;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loot-Kiste beim endgültigen Tod: Statt Items zu verstreuen, erscheint an
 * der Todesstelle eine Doppelkiste mit dem kompletten Inventar und einem
 * schwebenden Zähler darüber. Nach 60 Sekunden verschwindet sie samt Inhalt -
 * wer looten will, muss sich beeilen. Abbauen geht nicht, nur öffnen.
 */
public class VaroDeathChest implements Listener {

    private static final int DESPAWN_SECONDS = 60;
    private static final int SEARCH_UP = 6;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    /** Blockpositionen aller aktiven Kistenhälften - die sind unzerstörbar. */
    private final Set<Location> protectedBlocks = new HashSet<>();

    public VaroDeathChest(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Stellt die Kiste auf; gibt false zurück, wenn kein Platz war (dann normal droppen). */
    public boolean spawn(Player dead, List<ItemStack> drops) {
        World world = dead.getWorld();
        Block left = findSpot(world, dead.getLocation());
        if (left == null) {
            return false;
        }

        // Doppelkiste: LEFT verbindet sich (bei Blickrichtung Norden) nach Osten.
        Block right = left.getRelative(BlockFace.EAST);
        boolean isDouble = isFree(right);

        placeHalf(left, isDouble ? Chest.Type.LEFT : Chest.Type.SINGLE);
        if (isDouble) {
            placeHalf(right, Chest.Type.RIGHT);
        }

        Inventory inventory = ((org.bukkit.block.Chest) left.getState()).getInventory();
        for (ItemStack stack : drops) {
            for (ItemStack rest : inventory.addItem(stack).values()) {
                world.dropItemNaturally(left.getLocation().add(0.5, 1, 0.5), rest);
            }
        }

        protectedBlocks.add(left.getLocation());
        if (isDouble) {
            protectedBlocks.add(right.getLocation());
        }

        Location holoSpot = left.getLocation().add(isDouble ? 1.0 : 0.5, 1.4, 0.5);
        TextDisplay display = world.spawn(holoSpot, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
        });

        startCountdown(dead.getName(), display, left, isDouble ? right : null);
        return true;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (protectedBlocks.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cDie Loot-Kiste lässt sich nur öffnen, nicht abbauen.");
        }
    }

    private void startCountdown(String name, TextDisplay display, Block left, Block right) {
        new BukkitRunnable() {
            private int remaining = DESPAWN_SECONDS;

            @Override
            public void run() {
                if (remaining > 0) {
                    String color = remaining <= 10 ? "§c" : "§e";
                    display.text(LEGACY.deserialize("§f§l" + name + "'s Loot\n" + color + "⌛ " + remaining + "s"));
                    remaining--;
                    return;
                }

                cancel();
                display.remove();

                // Erst leeren: Wird ein Kistenblock ersetzt, kippt Minecraft
                // den Inhalt sonst auf den Boden - der Loot soll aber mit weg.
                if (left.getState() instanceof org.bukkit.block.Chest chest) {
                    Inventory inventory = chest.getInventory();
                    for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
                        viewer.closeInventory();
                    }
                    inventory.clear();
                }

                protectedBlocks.remove(left.getLocation());
                left.setType(Material.AIR);
                if (right != null) {
                    protectedBlocks.remove(right.getLocation());
                    right.setType(Material.AIR);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void placeHalf(Block block, Chest.Type type) {
        Chest data = (Chest) Material.CHEST.createBlockData();
        data.setFacing(BlockFace.NORTH);
        data.setType(type);
        block.setBlockData(data);
    }

    /** Sucht von der Todesstelle aus nach oben einen freien Block. */
    private Block findSpot(World world, Location death) {
        int x = death.getBlockX();
        int z = death.getBlockZ();
        int startY = Math.max(death.getBlockY(), world.getMinHeight());

        for (int y = startY; y <= startY + SEARCH_UP && y < world.getMaxHeight(); y++) {
            Block block = world.getBlockAt(x, y, z);
            if (isFree(block)) {
                return block;
            }
        }
        return null;
    }

    /** Luft, Wasser, Lava, Gras - alles, was eine Kiste ersetzen darf. */
    private boolean isFree(Block block) {
        return !block.getType().isSolid();
    }
}
