package de.kato.varo;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Hält die Koordinaten der aktuell aufgebauten Varo-Arena fest und
 * speichert sie in "arena.yml" im Plugin-Datenordner. So weiß
 * /varostart auch nach einem Server-Neustart noch, wo der Käfig aus
 * /varosetup steht.
 */
public class ArenaState {

    private static final String FILE_NAME = "arena.yml";

    public String worldName;
    public int centerX;
    public int centerZ;
    public int cageY;
    public int cageRadius;
    public int cageHeight;
    // true zwischen /varosetup und /varostart - solange steht der Käfig noch
    public boolean cageActive;

    /**
     * Lädt den gespeicherten Zustand. Gibt null zurück, wenn noch nie
     * /varosetup ausgeführt wurde.
     */
    public static ArenaState load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            return null;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ArenaState state = new ArenaState();
        state.worldName = config.getString("worldName");
        state.centerX = config.getInt("centerX");
        state.centerZ = config.getInt("centerZ");
        state.cageY = config.getInt("cageY");
        state.cageRadius = config.getInt("cageRadius");
        state.cageHeight = config.getInt("cageHeight");
        state.cageActive = config.getBoolean("cageActive");
        return state;
    }

    public void save(JavaPlugin plugin) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File file = new File(dataFolder, FILE_NAME);
        YamlConfiguration config = new YamlConfiguration();
        config.set("worldName", worldName);
        config.set("centerX", centerX);
        config.set("centerZ", centerZ);
        config.set("cageY", cageY);
        config.set("cageRadius", cageRadius);
        config.set("cageHeight", cageHeight);
        config.set("cageActive", cageActive);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte arena.yml nicht speichern: " + e.getMessage());
        }
    }
}