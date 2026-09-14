package de.kato.varo.listeners;

import de.kato.varo.VaroGame;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Drop-Regeln der Runde:
 * - Tiere geben die ganze Runde über nur Cooked Beef (plus Leder und Wolle),
 *   damit die Essens-Wirtschaft einheitlich bleibt.
 * - Autosmelter in der Farmzeit: Erze kommen als Barren, Monster-Fleisch
 *   gebraten. Spart den Ofen-Umweg; ab der Kampfphase wieder Vanilla.
 *
 * Block-Drops laufen in der Farmzeit komplett über den VaroHarvestListener
 * (der ruft {@link #smelt(ItemStack)} auf), hier bleiben die Mob-Drops.
 */
public class VaroSmeltListener implements Listener {

    private static final Map<Material, Material> SMELTED = Map.ofEntries(
            Map.entry(Material.RAW_IRON, Material.IRON_INGOT),
            Map.entry(Material.RAW_GOLD, Material.GOLD_INGOT),
            Map.entry(Material.RAW_COPPER, Material.COPPER_INGOT),
            Map.entry(Material.BEEF, Material.COOKED_BEEF),
            Map.entry(Material.PORKCHOP, Material.COOKED_PORKCHOP),
            Map.entry(Material.CHICKEN, Material.COOKED_CHICKEN),
            Map.entry(Material.MUTTON, Material.COOKED_MUTTON),
            Map.entry(Material.RABBIT, Material.COOKED_RABBIT),
            Map.entry(Material.COD, Material.COOKED_COD),
            Map.entry(Material.SALMON, Material.COOKED_SALMON)
    );

    private final VaroGame game;

    public VaroSmeltListener(VaroGame game) {
        this.game = game;
    }

    /** Gibt die geschmolzene/gebratene Variante zurück, sonst den Stack selbst. */
    public static ItemStack smelt(ItemStack stack) {
        Material smelted = SMELTED.get(stack.getType());
        return smelted == null ? stack : new ItemStack(smelted, stack.getAmount());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !game.isInActiveArena(killer)) {
            return;
        }

        VaroGame.Phase phase = game.getPhase();
        if (event.getEntity() instanceof Animals && (phase == VaroGame.Phase.FARM || phase == VaroGame.Phase.SHRINK)) {
            uniformFood(event.getDrops());
        } else if (phase == VaroGame.Phase.FARM) {
            event.getDrops().replaceAll(VaroSmeltListener::smelt);
        }
    }

    /**
     * Tiere geben nur eine Sorte Essen: Alles Essbare wird zu Cooked Beef,
     * damit niemand Hühnchen, Kaninchen und Fisch mitschleppt. Leder und Wolle
     * bleiben als Rohstoffe erhalten, alles andere (Federn, Hasenfell) fällt weg.
     */
    private void uniformFood(List<ItemStack> drops) {
        int meat = 0;

        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            Material type = stack.getType();
            if (type == Material.LEATHER || Tag.WOOL.isTagged(type)) {
                continue;
            }
            if (type.isEdible()) {
                meat += stack.getAmount();
            }
            iterator.remove();
        }

        if (meat > 0) {
            drops.add(new ItemStack(Material.COOKED_BEEF, meat));
        }
    }
}
