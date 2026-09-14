package de.kato.varo.listeners;

import de.kato.varo.ArenaState;
import de.kato.varo.VaroGame;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Sicherheitsnetz für die Join-Phase: Solange der Käfig aktiv ist (zwischen
 * /varosetup und /varostart), wird jeder neu joinende Spieler, der in der
 * Arena-Welt landet, automatisch in den Käfig teleportiert. Das verhindert,
 * dass Spieler an ihrer alten Logout-Position außerhalb der (gerade
 * verschobenen) Worldborder landen und dort sterben bzw. gar nicht erst
 * hineinteleportiert werden können.
 * Per /varosafenet <on|off> abschaltbar.
 */
public class VaroJoinListener implements Listener {

    private final VaroGame game;
    private boolean enabled = true;

    public VaroJoinListener(VaroGame game) {
        this.game = game;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) {
            return;
        }

        ArenaState arena = game.getArena();
        if (arena == null || !arena.cageActive) {
            return;
        }

        Player player = event.getPlayer();
        World arenaWorld = Bukkit.getWorld(arena.worldName);
        if (arenaWorld == null || !player.getWorld().equals(arenaWorld)) {
            return;
        }

        player.teleport(new Location(arenaWorld, arena.centerX + 0.5, arena.cageY + 1, arena.centerZ + 0.5));
    }
}
