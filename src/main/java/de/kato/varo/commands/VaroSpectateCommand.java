package de.kato.varo.commands;

import de.kato.varo.gui.VaroGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /spec - öffnet für Zuschauer die Liste der lebenden Spieler zum
 * Hinteleportieren. Ein Kompass-Item geht im Spectator-Modus nicht, weil
 * der Client dort keine Rechtsklicks auf Items sendet.
 */
public class VaroSpectateCommand implements CommandExecutor {

    private final VaroGui gui;

    public VaroSpectateCommand(VaroGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDieser Befehl kann nur von einem Spieler ausgeführt werden.");
            return true;
        }

        gui.openSpectate(player);
        return true;
    }
}
