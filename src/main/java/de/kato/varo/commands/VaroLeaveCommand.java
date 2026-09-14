package de.kato.varo.commands;

import de.kato.varo.ArenaState;
import de.kato.varo.VaroGame;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * /leave - bringt einen Spieler aus der Varo-Welt zurück zum Spawn.
 * Gedacht vor allem für Ausgeschiedene, die nach dem Tod als Zuschauer
 * zusehen und irgendwann wieder raus wollen. Das Inventar kommt dabei
 * automatisch zurück (siehe VaroInventoryListener).
 */
public class VaroLeaveCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final VaroGame game;

    public VaroLeaveCommand(JavaPlugin plugin, VaroGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDieser Befehl kann nur von einem Spieler ausgeführt werden.");
            return true;
        }

        ArenaState arena = game.getArena();
        if (arena == null || !player.getWorld().getName().equals(arena.worldName)) {
            player.sendMessage("§cDu bist gerade nicht in der Varo-Welt.");
            return true;
        }

        String lobbyName = plugin.getConfig().getString("lobby-world", "spawn");
        World lobby = Bukkit.getWorld(lobbyName);
        if (lobby == null) {
            // Fallback auf die Hauptwelt, falls der Name in der config nicht stimmt.
            lobby = Bukkit.getWorlds().get(0);
        }

        game.removeParticipant(player.getUniqueId());

        // Als Zuschauer würde man auch am Spawn durch Wände fliegen.
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        player.teleport(lobby.getSpawnLocation());
        player.sendMessage("§aDu hast Varo verlassen - willkommen zurück am Spawn.");
        return true;
    }
}
