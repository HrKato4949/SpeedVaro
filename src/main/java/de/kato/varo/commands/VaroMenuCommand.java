package de.kato.varo.commands;

import de.kato.varo.gui.VaroGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /varo - öffnet das Varo-Menü. /varo reload liest die config.yml neu ein
 * (Admins, auch von der Konsole), ohne den Server neu zu starten.
 */
public class VaroMenuCommand implements CommandExecutor {

    private final VaroGui gui;
    private final Runnable reload;

    public VaroMenuCommand(VaroGui gui, Runnable reload) {
        this.gui = gui;
        this.reload = reload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("varo.admin")) {
                sender.sendMessage("§cDazu hast du keine Rechte.");
                return true;
            }
            reload.run();
            sender.sendMessage("§aVaro-Config neu geladen.");
            sender.sendMessage("§7Neue Plugin-Versionen brauchen weiterhin einen Neustart.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDas Menü gibt es nur im Spiel. Von der Konsole geht §f/varo reload§c.");
            return true;
        }

        gui.openMain(player);
        return true;
    }
}
