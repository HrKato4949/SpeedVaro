package de.kato.varo;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Alle sichtbaren Texte kommen aus messages_<sprache>.yml. Die Sprache steht
 * in der config.yml ("language"), Standard Englisch. Beide Dateien werden in
 * den Plugin-Ordner kopiert, damit Admins Texte anpassen können; fehlt ein
 * Schlüssel in der gewählten Sprache, greift das englische Original.
 *
 * Platzhalter sind {0}, {1}, ...; Farbcodes werden mit & geschrieben.
 */
public final class Lang {

    private static final String DEFAULT_LANGUAGE = "en";

    private static YamlConfiguration messages = new YamlConfiguration();
    private static YamlConfiguration fallback = new YamlConfiguration();

    private Lang() {
    }

    public static void load(JavaPlugin plugin) {
        // Mitgelieferte Sprachdateien einmalig in den Datenordner legen.
        for (String language : new String[]{"en", "de"}) {
            String name = fileName(language);
            if (!new File(plugin.getDataFolder(), name).exists() && plugin.getResource(name) != null) {
                plugin.saveResource(name, false);
            }
        }

        String language = plugin.getConfig().getString("language", DEFAULT_LANGUAGE).toLowerCase();
        fallback = read(plugin, DEFAULT_LANGUAGE);
        messages = language.equals(DEFAULT_LANGUAGE) ? fallback : read(plugin, language);

        if (messages.getKeys(true).isEmpty()) {
            plugin.getLogger().warning("No messages for language '" + language + "' - falling back to English.");
            messages = fallback;
        }
    }

    /** Text mit ersetzten Platzhaltern und §-Farbcodes; fehlende Schlüssel zeigen den Schlüssel selbst. */
    public static String get(String key, Object... args) {
        String text = messages.getString(key, fallback.getString(key, key));
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text.replace('&', '§');
    }

    /** Mehrzeilige Texte (Lore) als Liste; jede Zeile mit denselben Platzhaltern. */
    public static List<String> list(String key, Object... args) {
        List<String> lines = messages.getStringList(key);
        if (lines.isEmpty()) {
            lines = fallback.getStringList(key);
        }
        return lines.stream().map(line -> {
            for (int i = 0; i < args.length; i++) {
                line = line.replace("{" + i + "}", String.valueOf(args[i]));
            }
            return line.replace('&', '§');
        }).toList();
    }

    private static YamlConfiguration read(JavaPlugin plugin, String language) {
        File file = new File(plugin.getDataFolder(), fileName(language));
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }

        InputStream resource = plugin.getResource(fileName(language));
        if (resource == null) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
    }

    private static String fileName(String language) {
        return "messages_" + language + ".yml";
    }
}
