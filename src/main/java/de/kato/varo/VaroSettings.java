package de.kato.varo;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Die im GUI einstellbaren Rundenwerte. Liegen in der config.yml, damit sie
 * einen Neustart überleben, und werden beim Setzen begrenzt, damit ein
 * verklickter Button keine unsinnige Runde erzeugt.
 */
public class VaroSettings {

    /** -1 bedeutet: Weltzeit nicht anfassen. */
    private static final long[] TIME_PRESETS = {-1, 1000, 6000, 12000, 18000};
    private static final String[] TIME_NAMES = {"Unverändert", "Morgen", "Mittag", "Abend", "Nacht"};

    private final JavaPlugin plugin;

    public VaroSettings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public int getBorderSize() {
        return plugin.getConfig().getInt("defaults.border-size", 2000);
    }

    public void setBorderSize(int value) {
        set("defaults.border-size", clamp(value, 500, 20_000));
    }

    public int getFarmMinutes() {
        return plugin.getConfig().getInt("defaults.farm-minutes", 20);
    }

    public void setFarmMinutes(int value) {
        set("defaults.farm-minutes", clamp(value, 1, 180));
    }

    public int getTargetSize() {
        return plugin.getConfig().getInt("defaults.target-size", 500);
    }

    public void setTargetSize(int value) {
        set("defaults.target-size", clamp(value, 50, getBorderSize()));
    }

    public int getShrinkMinutes() {
        return plugin.getConfig().getInt("defaults.shrink-minutes", 30);
    }

    public void setShrinkMinutes(int value) {
        set("defaults.shrink-minutes", clamp(value, 1, 180));
    }

    /** Respawns pro Spieler; 0 = klassisches Varo, ein Tod und man ist raus. */
    public int getLives() {
        return plugin.getConfig().getInt("defaults.lives", 3);
    }

    public void setLives(int value) {
        set("defaults.lives", clamp(value, 0, 5));
    }

    /** Sekunden Vorlauf mit Titel-Countdown, bevor der Käfig aufgeht; 0 = sofort. */
    public int getCountdownSeconds() {
        return plugin.getConfig().getInt("defaults.countdown-seconds", 5);
    }

    public void setCountdownSeconds(int value) {
        set("defaults.countdown-seconds", clamp(value, 0, 30));
    }

    /**
     * Radius in Chunks, der beim Aufbau rund um die Arena vorgeneriert wird.
     * 0 schaltet die Vorgenerierung ab.
     */
    public int getPreloadRadius() {
        return plugin.getConfig().getInt("defaults.preload-chunk-radius", 10);
    }

    public void setPreloadRadius(int value) {
        set("defaults.preload-chunk-radius", clamp(value, 0, 32));
    }

    /** Anzahl Chunks, die der aktuelle Radius abdeckt - fürs Menü. */
    public int getPreloadChunkCount() {
        int side = getPreloadRadius() * 2 + 1;
        return side * side;
    }

    /** Weltzeit, die beim Aufbau und beim Drop gesetzt wird; -1 = unverändert. */
    public long getStartTime() {
        return plugin.getConfig().getLong("defaults.start-time", 1000);
    }

    public String getStartTimeName() {
        long current = getStartTime();
        for (int i = 0; i < TIME_PRESETS.length; i++) {
            if (TIME_PRESETS[i] == current) {
                return TIME_NAMES[i];
            }
        }
        return String.valueOf(current);
    }

    /** Schaltet eine Voreinstellung weiter (direction 1 vor, -1 zurück). */
    public void cycleStartTime(int direction) {
        long current = getStartTime();

        int index = 0;
        for (int i = 0; i < TIME_PRESETS.length; i++) {
            if (TIME_PRESETS[i] == current) {
                index = i;
                break;
            }
        }

        int next = Math.floorMod(index + direction, TIME_PRESETS.length);
        plugin.getConfig().set("defaults.start-time", TIME_PRESETS[next]);
        plugin.saveConfig();
    }

    private void set(String path, int value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
