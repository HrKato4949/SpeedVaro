package de.kato.varo.listeners;

import de.kato.varo.VaroGame;
import de.kato.varo.VaroTeam;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;

/**
 * Schleichen + Rechtsklick auf einen Teamkollegen öffnet dessen Inventar -
 * zum schnellen Items-Tauschen ohne Ablegen. Das Inventar ist live, beide
 * sehen Änderungen sofort. Mit Schild in der Hand passiert nichts, damit
 * das Blocken im Kampf nicht dazwischenfunkt.
 */
public class VaroTeammateListener implements Listener {

    private final VaroGame game;

    public VaroTeammateListener(VaroGame game) {
        this.game = game;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        // Feuert für beide Hände - sonst öffnet sich das Inventar doppelt.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking() || !(event.getRightClicked() instanceof Player target)) {
            return;
        }

        // Mit Schild in der Hand ist der Rechtsklick zum Blocken da - im Kampf
        // darf sich hier kein Inventar öffnen.
        PlayerInventory inventory = player.getInventory();
        if (inventory.getItemInMainHand().getType() == Material.SHIELD
                || inventory.getItemInOffHand().getType() == Material.SHIELD) {
            return;
        }

        if (!game.isInActiveArena(player) || !game.isInActiveArena(target)) {
            return;
        }

        VaroTeam team = game.getTeam(player.getUniqueId());
        if (team == null || team != game.getTeam(target.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        player.openInventory(target.getInventory());
        player.sendMessage("§7Inventar von §f" + target.getName() + "§7 geöffnet.");
    }
}
