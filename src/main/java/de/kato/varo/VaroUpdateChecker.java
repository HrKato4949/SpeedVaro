package de.kato.varo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Fragt beim Start einmal bei Modrinth nach, ob es eine neuere Version gibt.
 * Läuft asynchron, der Serverstart wartet nicht darauf. Gibt es eine, steht
 * es in der Konsole, und Admins sehen es einmalig beim Joinen. Heruntergeladen
 * wird nichts - die Jar tauscht man weiterhin selbst. Abschaltbar über
 * update-check in der config.yml.
 */
public class VaroUpdateChecker implements Listener {

    // Projekt-ID statt Slug: Die bleibt auch bei einer Umbenennung gleich.
    private static final String API_URL = "https://api.modrinth.com/v2/project/qxPeA7DR/version";
    private static final String PAGE_URL = "https://modrinth.com/plugin/speedvaro";

    private final JavaPlugin plugin;
    private final String current;
    /** Neueste Version auf Modrinth, falls neuer als die laufende; sonst null. */
    private volatile String latest;
    private final Set<UUID> notified = new HashSet<>();

    public VaroUpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.current = plugin.getDescription().getVersion();
    }

    public void check() {
        if (!plugin.getConfig().getBoolean("update-check", true)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::fetch);
    }

    private void fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(10))
                    // Modrinth möchte wissen, wer anfragt.
                    .header("User-Agent", "HrKato4949/SpeedVaro/" + current)
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                plugin.getLogger().info("Update check skipped: Modrinth answered HTTP " + response.statusCode());
                return;
            }

            String newest = null;
            for (JsonElement element : JsonParser.parseString(response.body()).getAsJsonArray()) {
                JsonObject version = element.getAsJsonObject();
                // Nur fertige Releases melden, keine Alphas oder Betas.
                if (!"release".equals(version.get("version_type").getAsString())) {
                    continue;
                }
                String number = version.get("version_number").getAsString();
                if (newest == null || compare(number, newest) > 0) {
                    newest = number;
                }
            }

            if (newest != null && compare(newest, current) > 0) {
                latest = newest;
                plugin.getLogger().warning("A new version is available: " + newest
                        + " (you are running " + current + ") - " + PAGE_URL);
            }
        } catch (Exception e) {
            plugin.getLogger().info("Could not check for updates: " + e.getMessage());
        }
    }

    /** Admins erfahren es einmal pro Serverstart beim Joinen. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (latest == null || !player.hasPermission("varo.admin") || !notified.add(player.getUniqueId())) {
            return;
        }

        // Kurz warten, damit die Zeile nicht zwischen den Join-Meldungen untergeht.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(Lang.get("update.available", latest, current));
            }
        }, 40L);
    }

    /** Vergleicht "1.0.3" mit "1.2" zahlenweise; fehlende Stellen zählen als 0. */
    static int compare(String a, String b) {
        String[] partsA = numbers(a);
        String[] partsB = numbers(b);
        int length = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < length; i++) {
            int x = i < partsA.length ? parse(partsA[i]) : 0;
            int y = i < partsB.length ? parse(partsB[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    /** "v1.0.3-beta" wird zu ["1", "0", "3"]. */
    private static String[] numbers(String version) {
        String core = version.startsWith("v") ? version.substring(1) : version;
        int dash = core.indexOf('-');
        if (dash >= 0) {
            core = core.substring(0, dash);
        }
        return core.split("\\.");
    }

    private static int parse(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
