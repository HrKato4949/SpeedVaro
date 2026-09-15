package de.kato.varo;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Merkt sich dauerhaft alle bereits bespielten Arena-Zentren in
 * used-locations.yml, damit nie zweimal am selben Ort gespielt wird.
 */
public class UsedLocations {

    private static final String FILE_NAME = "used-locations.yml";

    private final JavaPlugin plugin;
    private final List<int[]> locations = new ArrayList<>();

    public UsedLocations(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Liegt der Punkt näher als minDistance an einem früheren Arena-Zentrum?
     * Mit minDistance = Bordergröße überschneiden sich zwei Spielfelder nie.
     */
    public boolean isTooClose(int x, int z, int minDistance) {
        long minSquared = (long) minDistance * minDistance;

        for (int[] used : locations) {
            long dx = x - (long) used[0];
            long dz = z - (long) used[1];
            if (dx * dx + dz * dz < minSquared) {
                return true;
            }
        }
        return false;
    }

    public void add(int x, int z) {
        locations.add(new int[]{x, z});
        save();
    }

    public int size() {
        return locations.size();
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String entry : config.getStringList("locations")) {
            String[] parts = entry.split(";");
            if (parts.length == 2) {
                locations.add(new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])});
            }
        }
    }

    private void save() {
        File folder = plugin.getDataFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }

        List<String> entries = new ArrayList<>(locations.size());
        for (int[] location : locations) {
            entries.add(location[0] + ";" + location[1]);
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("locations", entries);

        try {
            config.save(new File(folder, FILE_NAME));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save " + FILE_NAME + ": " + e.getMessage());
        }
    }
}
