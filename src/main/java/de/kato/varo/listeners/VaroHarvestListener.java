package de.kato.varo.listeners;

import de.kato.varo.VaroGame;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Schnelleres Farmen, nur während der Farmzeit:
 * - Abgebaute Blöcke und ihre Erfahrung landen direkt im Inventar; was nicht
 *   mehr passt, fällt wie gewohnt zu Boden. Erze kommen als Barren an.
 * - Bäume fallen komplett: Ein Stamm-Block reicht, und der ganze Baum samt
 *   Blättern wird geerntet - mit der Hand genauso wie mit der Axt. So kommt
 *   man schnell an Äpfel, Setzlinge und Holz.
 *
 * Als Baum zählt nur, was auch Blätter hat - eine Hütte aus Stämmen bleibt
 * stehen. Mitgenommen werden nur natürlich gewachsene Blätter (nicht
 * "persistent"), und zwar entlang ihres Stamm-Abstands nach außen: Sinkt der
 * Abstand wieder, gehört das Blatt schon zum Nachbarbaum. Ab der Kampfphase
 * gilt wieder Vanilla.
 */
public class VaroHarvestListener implements Listener {

    /** Obergrenze pro Baum - sonst fällt bei Riesen-Dschungelbäumen zu viel auf einmal. */
    private static final int MAX_TREE_BLOCKS = 400;

    private final VaroGame game;

    public VaroHarvestListener(VaroGame game) {
        this.game = game;
    }

    /** HIGH, damit der Lobby-Schutz vorher abbrechen konnte. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (game.getPhase() != VaroGame.Phase.FARM || !game.isInActiveArena(player)
                || player.getGameMode() != GameMode.SURVIVAL || !event.isDropItems()) {
            return;
        }

        Block origin = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();

        // Den angeschlagenen Block entfernt Vanilla selbst - nur die Drops
        // übernehmen wir, damit sie im Inventar statt am Boden landen.
        event.setDropItems(false);
        player.giveExp(event.getExpToDrop());
        event.setExpToDrop(0);
        give(player, origin, origin.getDrops(tool, player));

        if (Tag.LOGS.isTagged(origin.getType())) {
            fellTree(player, origin, tool);
        }
    }

    /** Sammelt ab dem angeschlagenen Stamm den ganzen Baum ein und erntet ihn. */
    private void fellTree(Player player, Block origin, ItemStack tool) {
        Material log = origin.getType();
        // Block -> Abstand zum Stamm (0 = Stamm selbst). Reihenfolge = Suchreihenfolge.
        Map<Block, Integer> tree = new LinkedHashMap<>();
        Deque<Block> queue = new ArrayDeque<>();
        tree.put(origin, 0);
        queue.add(origin);
        boolean hasLeaves = false;

        while (!queue.isEmpty() && tree.size() < MAX_TREE_BLOCKS) {
            Block block = queue.poll();
            int level = tree.get(block);

            // Alle 26 Nachbarn, weil Äste (Akazie, Dschungel) auch diagonal ansetzen.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block next = block.getRelative(dx, dy, dz);
                        if (tree.containsKey(next)) {
                            continue;
                        }

                        int nextLevel;
                        if (next.getType() == log) {
                            // Stämme nur über Stämme verbinden, nie über Blätter -
                            // sonst hängt der Nachbarbaum mit dran.
                            if (level > 0) {
                                continue;
                            }
                            nextLevel = 0;
                        } else {
                            nextLevel = leafDistance(next);
                            if (nextLevel < 0 || nextLevel < level) {
                                continue;
                            }
                            hasLeaves = true;
                        }

                        tree.put(next, nextLevel);
                        queue.add(next);
                    }
                }
            }
        }

        if (!hasLeaves) {
            return;
        }

        tree.remove(origin);
        int logs = 0;
        for (Map.Entry<Block, Integer> entry : tree.entrySet()) {
            Block block = entry.getKey();
            give(player, block, block.getDrops(tool, player));
            block.setType(Material.AIR);
            if (entry.getValue() == 0) {
                logs++;
            }
        }

        // Jeder Stamm kostet wie beim normalen Abbau Haltbarkeit (Unbreaking
        // zählt); Blätter sind wie in Vanilla gratis. Bei leerer Hand passiert nichts.
        if (logs > 0) {
            tool.damage(logs, player);
        }
    }

    /** Abstand natürlicher Blätter zum Stamm; -1 für alles andere, auch gesetzte Blätter. */
    private int leafDistance(Block block) {
        if (!Tag.LEAVES.isTagged(block.getType())
                || !(block.getBlockData() instanceof Leaves leaves) || leaves.isPersistent()) {
            return -1;
        }
        return leaves.getDistance();
    }

    /**
     * Legt Drops ins Inventar; was nicht mehr passt, fällt am Block zu Boden.
     * Erze werden dabei direkt zu Barren (Autosmelter der Farmzeit).
     */
    private void give(Player player, Block block, Collection<ItemStack> drops) {
        if (drops.isEmpty()) {
            return;
        }

        ItemStack[] smelted = drops.stream().map(VaroSmeltListener::smelt).toArray(ItemStack[]::new);
        for (ItemStack rest : player.getInventory().addItem(smelted).values()) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), rest);
        }
    }
}
