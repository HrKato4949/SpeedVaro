package de.kato.varo.commands;

import de.kato.varo.Lang;
import de.kato.varo.VaroGame;
import de.kato.varo.VaroTeam;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /backpack - öffnet die gemeinsame Kiste des eigenen Teams.
 */
public class VaroBackpackCommand implements CommandExecutor {

    private final VaroGame game;

    public VaroBackpackCommand(VaroGame game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("general.players-only"));
            return true;
        }

        openBackpack(player);
        return true;
    }

    /** Auch vom /varo-Menü aus aufgerufen. */
    public void openBackpack(Player player) {
        if (!game.isInActiveArena(player)) {
            player.sendMessage(Lang.get("general.arena-only"));
            return;
        }

        VaroTeam team = game.getTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Lang.get("team.none"));
            return;
        }

        player.openInventory(team.getBackpack());
    }
}
