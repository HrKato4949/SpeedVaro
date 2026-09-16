package de.kato.varo.listeners;

import de.kato.varo.Lang;
import de.kato.varo.VaroGame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sperrt in der Varo-Welt Befehle, die das Spiel aushebeln würden. Zwei
 * Listen in der config.yml: "blocked-commands" gilt, solange eine Runde
 * vorbereitet wird oder läuft, "farm-blocked-commands" nur in der Farmzeit.
 *
 * Die dauerhaft gesperrten Befehle sind auch von außen tabu, sobald ein
 * Varo-Spieler als Ziel genannt wird - sonst schickt jemand vom Spawn aus
 * /tpa an einen Teilnehmer oder holt ihn per /tpahere aus der Arena.
 */
public class VaroCommandListener implements Listener {

    private final JavaPlugin plugin;
    private final VaroGame game;
    private Set<String> blockedAlways = Set.of();
    private Set<String> blockedInFarm = Set.of();

    public VaroCommandListener(JavaPlugin plugin, VaroGame game) {
        this.plugin = plugin;
        this.game = game;
        reload();
    }

    /** Liest beide Listen neu aus der (bereits neu geladenen) Config. */
    public void reload() {
        blockedAlways = normalize(plugin.getConfig().getStringList("blocked-commands"));
        blockedInFarm = normalize(plugin.getConfig().getStringList("farm-blocked-commands"));
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] parts = event.getMessage().substring(1).split(" ");

        // "/economyshopgui:shop foo" -> "shop"
        String label = parts[0].toLowerCase(Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0) {
            label = label.substring(namespace + 1);
        }

        Player sender = event.getPlayer();
        if (game.isInActiveArena(sender)) {
            boolean blocked = blockedAlways.contains(label)
                    || (game.getPhase() == VaroGame.Phase.FARM && blockedInFarm.contains(label));
            if (blocked) {
                event.setCancelled(true);
                sender.sendMessage(Lang.get("protection.command-blocked"));
            }
            return;
        }

        // Von außerhalb: gesperrter Befehl mit einem Varo-Spieler als Argument.
        if (blockedAlways.contains(label)) {
            for (int i = 1; i < parts.length; i++) {
                Player target = Bukkit.getPlayerExact(parts[i]);
                if (target != null && game.isInActiveArena(target)) {
                    event.setCancelled(true);
                    sender.sendMessage(Lang.get("protection.target-in-arena", target.getName()));
                    return;
                }
            }
        }
    }

    private Set<String> normalize(List<String> commands) {
        Set<String> result = new HashSet<>();
        for (String command : commands) {
            result.add(command.toLowerCase(Locale.ROOT).replace("/", ""));
        }
        return result;
    }
}
