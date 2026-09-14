package de.kato.varo.listeners;

import de.kato.varo.VaroGame;
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
        if (!game.isInActiveArena(event.getPlayer())) {
            return;
        }

        // "/economyshopgui:shop foo" -> "shop"
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0) {
            label = label.substring(namespace + 1);
        }

        boolean blocked = blockedAlways.contains(label)
                || (game.getPhase() == VaroGame.Phase.FARM && blockedInFarm.contains(label));

        if (blocked) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cDieser Befehl ist während Varo gesperrt.");
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
