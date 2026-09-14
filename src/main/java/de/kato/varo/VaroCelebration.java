package de.kato.varo;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Siegesfeier am Rundenende: Titel und Fanfare für alle in der Arena,
 * eine Chat-Box für den ganzen Server, Feuerwerk in Teamfarbe über den
 * Siegern, die Belohnungen aus der config.yml - und nach zehn Sekunden
 * geht es für alle Teilnehmer zurück zum Spawn.
 *
 * Bewusst sparsam: acht Feuerwerke pro Sieger über vier Sekunden, sonst
 * nur Pakete - der Server merkt davon nichts.
 */
public class VaroCelebration {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int FIREWORK_ROUNDS = 8;
    private static final long FIREWORK_INTERVAL_TICKS = 10L;
    private static final int SEND_HOME_DELAY_SECONDS = 10;
    private static final String LINE = "§8§m                                            ";

    private final JavaPlugin plugin;
    private final VaroGame game;
    private final Random random = new Random();

    public VaroCelebration(JavaPlugin plugin, VaroGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    /**
     * @param winnerIds  alle Sieger (Teammitglieder oder ein Einzelspieler)
     * @param winnerName Anzeigename inkl. Farbcode, z.B. "§cTeam 1" oder "Max"
     * @param team       das Siegerteam oder null bei einem Einzelspieler
     */
    public void celebrate(Set<UUID> winnerIds, String winnerName, VaroTeam team) {
        List<Player> online = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (UUID uuid : winnerIds) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                online.add(player);
            }
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null) {
                names.add(name);
            }
        }

        showTitle(winnerName);
        broadcastChatBox(winnerName, names);
        reward(names);

        Color color = team == null ? Color.fromRGB(0xFFAA00) : Color.fromRGB(team.getColor().value());
        launchFireworks(online, color);

        Bukkit.getScheduler().runTaskLater(plugin, this::sendParticipantsHome, SEND_HOME_DELAY_SECONDS * 20L);
    }

    /**
     * Nach der Feier zurück zum Spawn - Sieger wie Zuschauer. Das Inventar
     * tauscht der VaroInventoryListener beim Weltwechsel automatisch zurück.
     */
    private void sendParticipantsHome() {
        World lobby = Bukkit.getWorld(plugin.getConfig().getString("lobby-world", "spawn"));
        if (lobby == null) {
            lobby = Bukkit.getWorlds().get(0);
        }

        // Kopie, weil das Teleportieren die Spielerliste der Welt verändert.
        for (Player player : new ArrayList<>(arenaPlayers())) {
            if (!game.isParticipant(player.getUniqueId())) {
                continue;
            }

            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
            }
            player.teleport(lobby.getSpawnLocation());
            player.sendMessage("§7Die Runde ist vorbei - willkommen zurück am Spawn.");
        }
    }

    private void showTitle(String winnerName) {
        Title title = Title.title(
                LEGACY.deserialize("§6§l" + winnerName),
                LEGACY.deserialize("§egewinnt Varo!"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1)));
        Sound fanfare = Sound.sound(Key.key("ui.toast.challenge_complete"), Sound.Source.MASTER, 1f, 1f);

        for (Player player : arenaPlayers()) {
            player.showTitle(title);
            player.playSound(fanfare);
        }
    }

    private void broadcastChatBox(String winnerName, List<String> names) {
        String rewardText = plugin.getConfig().getString("win-rewards-text", "").replace('&', '§');

        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(LINE);
        lines.add("");
        lines.add("   §6§l★ §e§lVARO SIEGER §6§l★");
        lines.add("   §f" + winnerName + (names.isEmpty() ? "" : " §7» §f" + String.join("§7, §f", names)));
        if (!rewardText.isEmpty()) {
            lines.add("   §7Belohnung: " + rewardText);
        }
        lines.add("");
        lines.add(LINE);
        lines.add("");

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (String line : lines) {
                player.sendMessage(line);
            }
        }
    }

    /** Belohnungen laufen als Konsolenbefehle, damit jedes Economy-Plugin passt. */
    private void reward(List<String> names) {
        List<String> commands = plugin.getConfig().getStringList("win-rewards");
        for (String name : names) {
            for (String command : commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", name));
            }
        }
    }

    private void launchFireworks(List<Player> winners, Color color) {
        if (winners.isEmpty()) {
            return;
        }

        Sound levelUp = Sound.sound(Key.key("entity.player.levelup"), Sound.Source.PLAYER, 1f, 1f);
        for (Player winner : winners) {
            winner.playSound(levelUp);
        }

        new BukkitRunnable() {
            private int round;

            @Override
            public void run() {
                if (round++ >= FIREWORK_ROUNDS) {
                    cancel();
                    return;
                }

                for (Player winner : winners) {
                    if (winner.isOnline()) {
                        spawnFirework(winner.getLocation(), color);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, FIREWORK_INTERVAL_TICKS);
    }

    private void spawnFirework(Location around, Color color) {
        Location spawn = around.clone().add(random.nextDouble() * 4 - 2, 1, random.nextDouble() * 4 - 2);
        FireworkEffect.Type[] types = FireworkEffect.Type.values();

        around.getWorld().spawn(spawn, Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(types[random.nextInt(types.length)])
                    .withColor(color, Color.WHITE)
                    .withFade(color)
                    .withFlicker()
                    .withTrail()
                    .build());
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        });
    }

    private List<Player> arenaPlayers() {
        ArenaState arena = game.getArena();
        World world = arena == null ? null : Bukkit.getWorld(arena.worldName);
        return world == null ? List.of() : world.getPlayers();
    }
}
