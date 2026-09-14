package de.kato.varo.commands;

import de.kato.varo.ArenaState;
import de.kato.varo.Cage;
import de.kato.varo.VaroGame;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * /varoreset - macht die Varo-Welt zwischen zwei Runden wieder normal
 * betretbar: Border zurück auf Maximum und Mitte, Weltspawn auf sicheren
 * Boden, Käfigreste weg, Zuschauer zurück in den Adventure-Modus.
 *
 * Ohne das bleibt nach einer Runde eine kleine Border weit draußen stehen -
 * Multiverse verweigert dann den Teleport in die Welt ("location is deemed
 * unsafe" bzw. "left the confines of this world").
 */
public class VaroResetCommand implements CommandExecutor {

    /** Größtes vom Server akzeptiertes Border-Maß. */
    private static final double MAX_BORDER_SIZE = 59_999_968;

    private final JavaPlugin plugin;
    private final VaroGame game;

    public VaroResetCommand(JavaPlugin plugin, VaroGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        resetWorld(sender);
        return true;
    }

    /** Auch vom /varo-Menü aus aufgerufen. */
    public void resetWorld(CommandSender sender) {

        String arenaWorld = plugin.getConfig().getString("arena-world", "varo");
        World world = Bukkit.getWorld(arenaWorld);
        if (world == null) {
            sender.sendMessage("§cDie Welt §f'" + arenaWorld + "'§c wurde nicht gefunden.");
            return;
        }

        // Käfig der letzten Runde abräumen, falls er noch steht.
        ArenaState arena = game.getArena();
        if (arena != null && arena.cageActive && arenaWorld.equals(arena.worldName)) {
            Cage.clear(world, arena);
            arena.cageActive = false;
            arena.save(plugin);
        }

        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(MAX_BORDER_SIZE);

        world.setSpawnLocation(0, world.getHighestBlockYAt(0, 0) + 1, 0);

        int sentHome = sendEveryoneHome(world);
        game.reset();

        sender.sendMessage("§aVaro-Welt zurückgesetzt.");
        sender.sendMessage("§7Border auf Maximum, Weltspawn bei 0/0, Käfig entfernt.");
        sender.sendMessage("§7Zum Spawn geschickt: §f" + sentHome + " Spieler§7.");
    }

    /**
     * Schickt alle aus der Varo-Welt zurück zum Spawn. Das Aufräumen des
     * Inventars übernimmt bewusst der VaroInventoryListener beim Weltwechsel:
     * Er leert den Varo-Loot und spielt das gesicherte Inventar zurück. Hier
     * selbst zu leeren würde jedem, für den es keine Sicherung gibt, sein
     * echtes Inventar löschen.
     */
    private int sendEveryoneHome(World world) {
        World lobby = Bukkit.getWorld(plugin.getConfig().getString("lobby-world", "spawn"));
        if (lobby == null) {
            lobby = Bukkit.getWorlds().get(0);
        }

        // Kopie, weil das Teleportieren die Spielerliste der Welt verändert.
        List<Player> players = new ArrayList<>(world.getPlayers());
        for (Player player : players) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
            }

            player.teleport(lobby.getSpawnLocation());
            player.sendMessage("§eDie Varo-Runde wurde beendet - du bist zurück am Spawn.");
        }

        return players.size();
    }
}
