package de.kato.varo.listeners;

import de.kato.varo.Lang;
import de.kato.varo.VaroGame;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * /anvil und /enchant: Wer Amboss bzw. Zaubertisch im Inventar hat, öffnet
 * ihn direkt - ohne Hinstellen. Der virtuelle Zaubertisch rechnet wie einer
 * mit 15 Bücherregalen, sonst gäbe es ohne Regale nur Stufe-1-Kram.
 */
public class VaroWorkstationListener implements Listener, CommandExecutor {

    private static final int FULL_BOOKSHELVES = 15;

    private final VaroGame game;
    private final Random random = new Random();
    /** Wer gerade einen virtuellen Zaubertisch offen hat - nur der wird verstärkt. */
    private final Set<UUID> virtualTables = new HashSet<>();

    public VaroWorkstationListener(VaroGame game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("general.players-only"));
            return true;
        }

        if (!game.isInActiveArena(player)) {
            player.sendMessage(Lang.get("general.arena-only"));
            return true;
        }

        PlayerInventory inventory = player.getInventory();
        if (command.getName().equals("varoanvil")) {
            if (!inventory.contains(Material.ANVIL)
                    && !inventory.contains(Material.CHIPPED_ANVIL)
                    && !inventory.contains(Material.DAMAGED_ANVIL)) {
                player.sendMessage(Lang.get("workstation.need-anvil"));
                return true;
            }
            player.openAnvil(null, true);
            return true;
        }

        if (!inventory.contains(Material.ENCHANTING_TABLE)) {
            player.sendMessage(Lang.get("workstation.need-table"));
            return true;
        }
        virtualTables.add(player.getUniqueId());
        player.openEnchanting(null, true);
        return true;
    }

    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (!virtualTables.contains(event.getEnchanter().getUniqueId())) {
            return;
        }

        // Vanilla-Formel mit 15 Regalen: Basis 8..30, dritter Slot immer 30.
        int base = random.nextInt(8) + 1 + FULL_BOOKSHELVES / 2 + random.nextInt(FULL_BOOKSHELVES + 1);
        int[] costs = {Math.max(base / 3, 1), base * 2 / 3 + 1, Math.max(base, FULL_BOOKSHELVES * 2)};

        EnchantmentOffer[] offers = event.getOffers();
        for (int slot = 0; slot < offers.length && slot < costs.length; slot++) {
            if (offers[slot] != null) {
                offers[slot].setCost(costs[slot]);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        virtualTables.remove(event.getPlayer().getUniqueId());
    }
}
