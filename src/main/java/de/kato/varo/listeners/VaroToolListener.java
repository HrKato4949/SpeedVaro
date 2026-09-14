package de.kato.varo.listeners;

import de.kato.varo.VaroGame;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

/**
 * Schnellerer Einstieg in die Farmzeit: Wer in der Varo-Welt ein Holzwerkzeug
 * craftet, bekommt stattdessen das Eisen-Gegenstück; Diamantwerkzeuge bleiben
 * Diamant. Beide kommen mit Effizienz IV und Haltbarkeit III. Schwerter sind
 * bewusst ausgenommen.
 *
 * Gehookt wird das Ergebnisfeld der Werkbank (PrepareItemCraftEvent), nicht
 * der Klick - so sieht der Spieler schon vorher, was er bekommt, und
 * Shift-Klick-Crafting funktioniert genauso.
 */
public class VaroToolListener implements Listener {

    private static final Map<Material, Material> UPGRADES = Map.of(
            Material.WOODEN_PICKAXE, Material.IRON_PICKAXE,
            Material.WOODEN_AXE, Material.IRON_AXE,
            Material.WOODEN_SHOVEL, Material.IRON_SHOVEL,
            Material.WOODEN_HOE, Material.IRON_HOE,
            Material.DIAMOND_PICKAXE, Material.DIAMOND_PICKAXE,
            Material.DIAMOND_AXE, Material.DIAMOND_AXE,
            Material.DIAMOND_SHOVEL, Material.DIAMOND_SHOVEL,
            Material.DIAMOND_HOE, Material.DIAMOND_HOE
    );

    private final VaroGame game;

    public VaroToolListener(VaroGame game) {
        this.game = game;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null) {
            return;
        }

        Material upgraded = UPGRADES.get(result.getType());
        if (upgraded == null) {
            return;
        }

        if (!(event.getView().getPlayer() instanceof Player player) || !game.isInActiveArena(player)) {
            return;
        }

        ItemStack tool = new ItemStack(upgraded);
        ItemMeta meta = tool.getItemMeta();
        meta.addEnchant(Enchantment.EFFICIENCY, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        tool.setItemMeta(meta);

        event.getInventory().setResult(tool);
    }
}
