package de.kato.varo.commands;

import de.kato.varo.Lang;
import de.kato.varo.VaroGame;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /varocancel - lehnt eine offene Einladung ab.
 */
public class VaroCancelCommand implements CommandExecutor {

    private final VaroGame game;

    public VaroCancelCommand(VaroGame game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("general.players-only"));
            return true;
        }

        if (!game.isInvited(player.getUniqueId())) {
            player.sendMessage(Lang.get("join.not-invited"));
            return true;
        }

        game.uninvite(player.getUniqueId());
        player.sendMessage(Lang.get("join.declined"));
        return true;
    }
}
