package de.kato.varo;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Nachtsicht während der Farmzeit: Wer beim Drop oder nach einem Respawn in
 * die Arena gleitet, sieht auch nachts und in Höhlen etwas. Mit Beginn des
 * Kampfes und beim Verlassen der Arena-Welt ist der Effekt wieder weg.
 * Abschaltbar über farm-night-vision in der config.yml.
 */
public class VaroNightVision implements Listener {

    private final JavaPlugin plugin;
    private final VaroGame game;

    public VaroNightVision(JavaPlugin plugin, VaroGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    public void apply(Player player) {
        if (!plugin.getConfig().getBoolean("farm-night-vision", true)) {
            return;
        }
        // Unendlich statt "sehr lang": Nachtsicht flackert in den letzten
        // Sekunden, und das soll nie passieren. Ohne Partikel und Ambient,
        // nur das Icon oben rechts bleibt.
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                PotionEffect.INFINITE_DURATION, 0, false, false, true));
    }

    /** Farmzeit vorbei: allen in der Arena-Welt die Nachtsicht nehmen. */
    public void removeAll(World world) {
        for (Player player : world.getPlayers()) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    /** Wer die Arena verlässt (/leave, Reset, Sieg), nimmt den Effekt nicht mit. */
    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        ArenaState arena = game.getArena();
        if (arena != null && event.getFrom().getName().equals(arena.worldName)) {
            event.getPlayer().removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }
}
