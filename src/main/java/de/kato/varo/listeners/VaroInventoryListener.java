package de.kato.varo.listeners;

import de.kato.varo.ArenaState;
import de.kato.varo.VaroGame;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Trennt das Varo-Inventar vom normalen Server-Inventar: Beim Betreten der
 * Varo-Welt wird das mitgebrachte Inventar gesichert und geleert, beim
 * Verlassen wieder eingespielt. Der in Varo gesammelte Loot bleibt dadurch
 * in der Varo-Welt.
 *
 * Gesichert wird pro Spieler in inventories/&lt;uuid&gt;.yml - bewusst auf der
 * Platte statt nur im Speicher, damit ein Serverneustart niemandem sein
 * Inventar kostet.
 */
public class VaroInventoryListener implements Listener {

    private static final String FOLDER_NAME = "inventories";

    private final JavaPlugin plugin;
    private final VaroGame game;

    public VaroInventoryListener(JavaPlugin plugin, VaroGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        String varoWorld = varoWorldName();
        if (varoWorld == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getWorld().getName().equals(varoWorld)) {
            enterVaro(player);
        } else if (event.getFrom().getName().equals(varoWorld)) {
            leaveVaro(player);
        }
    }

    /**
     * Beim Tod in der Varo-Welt landet man je nach Spawnpunkt direkt in einer
     * anderen Welt - dabei feuert kein PlayerChangedWorldEvent, deshalb hier
     * nachziehen. MONITOR, damit wir die endgültige Respawn-Position sehen,
     * nachdem alle anderen Plugins sie gesetzt haben.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        String varoWorld = varoWorldName();
        if (varoWorld == null || event.getRespawnLocation().getWorld().getName().equals(varoWorld)) {
            return;
        }

        Player player = event.getPlayer();
        // Erst nach dem Respawn greift setContents zuverlässig.
        Bukkit.getScheduler().runTask(plugin, () -> leaveVaro(player));
    }

    private void enterVaro(Player player) {
        File file = file(player.getUniqueId());

        // Gibt es schon ein Backup, hat der Spieler sein Inventar noch nicht
        // zurückbekommen - dann auf keinen Fall mit dem Varo-Stand überschreiben.
        if (file.exists()) {
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("contents", Arrays.asList(player.getInventory().getContents()));
        config.set("level", player.getLevel());
        config.set("exp", player.getExp());

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte Inventar von " + player.getName()
                    + " nicht sichern: " + e.getMessage());
            // Ohne Backup wird garantiert nichts geleert.
            return;
        }

        player.getInventory().clear();
        player.setLevel(0);
        player.setExp(0f);
    }

    private void leaveVaro(Player player) {
        File file = file(player.getUniqueId());
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> stored = config.getList("contents");

        // Varo-Loot bleibt in Varo: erst leeren, dann das Original einspielen.
        player.getInventory().clear();

        if (stored != null) {
            ItemStack[] contents = new ItemStack[player.getInventory().getSize()];
            for (int i = 0; i < contents.length && i < stored.size(); i++) {
                contents[i] = stored.get(i) instanceof ItemStack stack ? stack : null;
            }
            player.getInventory().setContents(contents);
        }

        player.setLevel(config.getInt("level"));
        player.setExp((float) config.getDouble("exp"));

        if (!file.delete()) {
            plugin.getLogger().warning("Konnte " + file.getName() + " nicht löschen.");
        }
    }

    private String varoWorldName() {
        ArenaState arena = game.getArena();
        return arena == null ? null : arena.worldName;
    }

    private File file(UUID uuid) {
        File folder = new File(plugin.getDataFolder(), FOLDER_NAME);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return new File(folder, uuid + ".yml");
    }
}
