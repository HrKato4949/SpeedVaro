package de.kato.varo.commands;

import de.kato.varo.listeners.VaroJoinListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /varosafenet <on|off> - schaltet das Join-Sicherheitsnetz (automatische
 * Teleportation neu joinender Spieler in den aktiven Käfig) ein oder aus.
 */
public class VaroSafenetCommand implements CommandExecutor {

    private final VaroJoinListener joinListener;

    public VaroSafenetCommand(VaroJoinListener joinListener) {
        this.joinListener = joinListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !(args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) {
            sender.sendMessage("§cBenutzung: /varosafenet <on|off>");
            sender.sendMessage("§7Aktuell: " + (joinListener.isEnabled() ? "§aan" : "§caus"));
            return true;
        }

        boolean enable = args[0].equalsIgnoreCase("on");
        joinListener.setEnabled(enable);
        sender.sendMessage(enable
                ? "§aJoin-Sicherheitsnetz aktiviert."
                : "§cJoin-Sicherheitsnetz deaktiviert.");
        return true;
    }
}
