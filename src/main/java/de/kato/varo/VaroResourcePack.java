package de.kato.varo;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Optionales Resourcepack: eigene Menü-Icons, Herz-Symbole, Phasen-Symbole
 * und eine eigene Schrift für Titel und Teamnamen. Steht in der config.yml
 * eine URL, schickt der Server das Pack beim Joinen. Wer es ablehnt oder
 * nicht laden kann, sieht weiterhin die Standard-Darstellung - alle Stellen
 * fragen vorher {@link #has(Player)}.
 *
 * Die Prüfsumme wird beim Start aus der Datei berechnet, damit sie niemand
 * von Hand pflegen muss; ohne Prüfsumme würde der Client das Pack bei jedem
 * Join neu herunterladen.
 */
public class VaroResourcePack implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final Set<UUID> loaded = new HashSet<>();

    private String url = "";
    private boolean required;
    /** Feste ID pro URL - so ersetzt ein erneutes Senden das Pack statt es zu stapeln. */
    private UUID id;
    private volatile byte[] hash;

    public VaroResourcePack(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Beim Start und bei /varo reload: URL lesen und Prüfsumme neu berechnen. */
    public void load() {
        url = plugin.getConfig().getString("resource-pack.url", "").trim();
        required = plugin.getConfig().getBoolean("resource-pack.required", false);
        hash = null;
        if (url.isEmpty()) {
            return;
        }
        id = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::fetchHash);
    }

    private void fetchHash() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "HrKato4949/SpeedVaro")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    // GitHub-Releases leiten auf einen Download-Server weiter.
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("Resource pack not available (HTTP " + response.statusCode() + "): " + url);
                return;
            }

            byte[] digest = MessageDigest.getInstance("SHA-1").digest(response.body());
            hash = digest;
            plugin.getLogger().info("Resource pack ready (" + response.body().length / 1024 + " KB, sha1 "
                    + hex(digest) + ").");

            // Wer beim Start schon online war, bekommt es jetzt nachgereicht.
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    send(player);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load the resource pack from " + url + ": " + e.getMessage());
        }
    }

    /** Hat dieser Spieler das Pack geladen? Entscheidet über Icons, Glyphen und Schrift. */
    public boolean has(Player player) {
        return loaded.contains(player.getUniqueId());
    }

    /**
     * /varo pack - schaltet die Pack-Darstellung von Hand um. Für alle, die
     * das Pack selbst im Client geladen haben (etwa beim Zeichnen der
     * Texturen), weil der Server davon nichts erfährt. Gibt zurück, ob sie
     * jetzt an ist.
     */
    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (loaded.remove(uuid)) {
            return false;
        }
        loaded.add(uuid);
        return true;
    }

    private void send(Player player) {
        byte[] current = hash;
        if (current != null) {
            player.setResourcePack(id, url, current, LEGACY.deserialize(Lang.get("resource-pack.prompt")), required);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        // Andere Packs (server.properties, andere Plugins) gehen uns nichts an.
        if (id == null || !id.equals(event.getID())) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(uuid);
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, DISCARDED -> loaded.remove(uuid);
            default -> { }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
