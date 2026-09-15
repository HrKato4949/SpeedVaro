package de.kato.varo.commands;

import de.kato.varo.Lang;
import de.kato.varo.gui.VaroGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /varo - öffnet das Varo-Menü. /varo reload liest die config.yml und die
 * Sprachdateien neu ein (Admins, auch von der Konsole), ohne Neustart.
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
                sender.sendMessage(Lang.get("general.no-permission"));
                return true;
            }
            reload.run();
            sender.sendMessage(Lang.get("reload.done"));
            sender.sendMessage(Lang.get("reload.jar-hint"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("reload.console-hint"));
            return true;
        }

        gui.openMain(player);
        return true;
    }
}
