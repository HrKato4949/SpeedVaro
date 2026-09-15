package de.kato.varo.listeners;

import de.kato.varo.ArenaState;
import de.kato.varo.Lang;
import de.kato.varo.VaroCelebration;
import de.kato.varo.VaroDeathChest;
import de.kato.varo.VaroGame;
import de.kato.varo.VaroGlideManager;
import de.kato.varo.VaroNightVision;
import de.kato.varo.VaroTeam;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Leben, Ausscheiden und Sieg.
 *
 * Wer in der Arena stirbt, überspringt den Todesbildschirm und schwebt sofort
 * als Zuschauer hoch über dem Arena-Zentrum. Jedes Herz ist ein Respawn: Mit
 * Herz übrig erlischt eines, er behält sein Inventar, sieht einen
 * 5-Sekunden-Countdown und wird erneut mit Elytra gedroppt. Wer ohne Herz
 * stirbt, ist raus - der Loot landet in einer Kiste mit Zähler
 * (VaroDeathChest), der Spieler bleibt als Zuschauer in der Welt. Bleibt nur noch eine Partei übrig (ein Team
 * oder ein teamloser Spieler), ist die Runde vorbei.
 */
public class VaroDeathListener implements Listener {

    private static final int REDROP_DELAY_SECONDS = 5;
    private static final long BOSSBAR_TICKS = 100L;
    private static final String SEPARATOR = "§8§m                                        ";
    /** So lange wird der Zuschauer-Modus nach dem Tod jeden Tick nachgezogen. */
    private static final int SPECTATOR_GUARD_TICKS = 40;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final VaroGame game;
    private final VaroGlideManager glideManager;
    private final VaroCelebration celebration;
    private final VaroDeathChest deathChest;
    private final VaroNightVision nightVision;

    /** In der Arena gestorben - der Respawn gehört hoch über das Zentrum. */
    private final Set<UUID> pendingRespawn = new HashSet<>();

    public VaroDeathListener(JavaPlugin plugin, VaroGame game, VaroGlideManager glideManager,
                             VaroCelebration celebration, VaroDeathChest deathChest,
                             VaroNightVision nightVision) {
        this.plugin = plugin;
        this.game = game;
        this.glideManager = glideManager;
        this.celebration = celebration;
        this.deathChest = deathChest;
        this.nightVision = nightVision;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!isRunning()) {
            return;
        }

        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        if (!game.isAlive(uuid) || !game.isInActiveArena(player)) {
            return;
        }

        // Jedes Herz ist ein Respawn: Beim Tod erlischt eines und es geht
        // weiter. Erst wer ohne Herz stirbt, ist raus.
        boolean redrop = game.getLives(uuid) > 0;
        if (redrop) {
            int livesLeft = game.loseLife(uuid);

            // Kein Loot-Verlust - gleich geht es von oben weiter.
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);

            announce(Lang.get("death.died", player.getName(), livesLeft));
            if (livesLeft == 0) {
                player.sendMessage(Lang.get("death.last-heart"));
            }
        } else {
            game.eliminate(uuid);
            // Falls er mitten im Flug starb: Flug-Elytra weg, Brustteil zurück.
            glideManager.endGlide(player);

            // Loot direkt aus dem Inventar nehmen statt aus den Event-Drops:
            // Die sind bei keepInventory=true leer, und so wandern exakt die
            // echten Items (Haltbarkeit, Verzauberungen) in die Kiste.
            List<ItemStack> loot = new ArrayList<>();
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && !stack.getType().isAir()) {
                    loot.add(stack.clone());
                }
            }
            event.getDrops().clear();
            event.setKeepInventory(true);
            player.getInventory().clear();
            event.setKeepLevel(false);
            event.setDroppedExp(Math.min(player.getLevel() * 7, 100));

            if (!deathChest.spawn(player, loot)) {
                for (ItemStack stack : loot) {
                    player.getWorld().dropItemNaturally(player.getLocation(), stack);
                }
            }

            // Beendet dieser Tod die Runde, übernimmt die Siegesfeier - Sound
            // und Bossbar des Ausscheidens würden sonst mit ihr kollidieren.
            if (checkWinner()) {
                announce(Lang.get("death.eliminated-short", player.getName()));
            } else {
                eliminated(player.getName());
            }
        }

        // Todesbildschirm überspringen: sofort respawnen und als Zuschauer
        // über die Arena setzen. Das passiert bewusst hier und nicht erst im
        // Respawn-Event - so hängt es nicht davon ab, wann der Respawn (etwa
        // nach dem Laden des Ziel-Chunks) tatsächlich durch ist.
        pendingRespawn.add(uuid);
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.spigot().respawn();
            spectate(player, redrop);
        });
    }

    /**
     * HIGHEST, damit unsere Respawn-Position nach EssentialsSpawn & Co. gesetzt
     * wird - sonst landet man am Server-Spawn statt über der Arena.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!pendingRespawn.remove(event.getPlayer().getUniqueId())) {
            return;
        }

        Location sky = game.getSkyLocation();
        if (sky != null) {
            event.setRespawnLocation(sky);
        }
    }

    /**
     * Wer während der Wartezeit offline ging, hinge sonst im Zuschauer-Modus
     * fest - und wer offline aus der Runde geworfen wurde, käme sonst im
     * Survival zurück und könnte weiterspielen.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!isRunning() || !game.isParticipant(uuid) || !game.isInActiveArena(player)) {
            return;
        }

        boolean alive = game.isAlive(uuid);
        boolean spectator = player.getGameMode() == GameMode.SPECTATOR;
        if (alive && spectator) {
            spectate(player, true);
        } else if (!alive && !spectator) {
            spectate(player, false);
        }
    }

    /**
     * Wirft einen Spieler aus der Runde - gedacht für Leute, die offline
     * gegangen sind und sonst den Sieg blockieren. Online-Spieler gehen zurück
     * zum Spawn; Offline-Spieler bleiben als ausgeschieden vermerkt und werden
     * beim Wiederkommen Zuschauer.
     */
    public void kick(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        Player online = Bukkit.getPlayer(uuid);

        if (online != null) {
            game.removeParticipant(uuid);
            World lobby = Bukkit.getWorld(plugin.getConfig().getString("lobby-world", "world"));
            if (lobby == null) {
                lobby = Bukkit.getWorlds().get(0);
            }
            online.setGameMode(GameMode.SURVIVAL);
            online.teleport(lobby.getSpawnLocation());
            online.sendMessage(Lang.get("kick.you-were-removed"));
        } else {
            game.eliminate(uuid);
        }

        announce(Lang.get("kick.removed", name, game.getAlive().size()));
        if (isRunning()) {
            checkWinner();
        }
    }

    /**
     * Endgültiges Aus: Sound, eine schmale Bossbar ganz oben (verdeckt im
     * Kampf nichts) und eine auffällige Chat-Meldung. Bewusst kein Titel in
     * der Bildschirmmitte.
     */
    private void eliminated(String name) {
        int remaining = game.getAlive().size();
        BossBar bar = BossBar.bossBar(
                LEGACY.deserialize(Lang.get("death.eliminated-bossbar", name, remaining)),
                1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        Sound doom = Sound.sound(Key.key("entity.wither.spawn"), Sound.Source.MASTER, 0.8f, 1f);

        ArenaState arena = game.getArena();
        World world = arena == null ? null : Bukkit.getWorld(arena.worldName);
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            player.showBossBar(bar);
            player.playSound(doom);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bar);
            }
        }, BOSSBAR_TICKS);

        announce(SEPARATOR);
        announce(Lang.get("death.eliminated-chat", name, remaining));
        announce(SEPARATOR);
    }

    /**
     * Parkt den Spieler als Zuschauer über dem Arena-Zentrum. Gamemode und
     * Position werden die ersten Ticks jeden Tick nachgezogen: Nur einmal
     * gesetzt gingen sie verloren, sobald der Respawn erst danach greift -
     * der Spieler stand dann im Survival in der Luft und fiel ohne Elytra.
     * Mit redrop läuft dabei der Countdown, danach geht es erneut von oben los.
     */
    private void spectate(Player player, boolean redrop) {
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                UUID uuid = player.getUniqueId();
                if (!player.isOnline() || !game.isParticipant(uuid)
                        || (redrop && (!isRunning() || !game.isAlive(uuid)))) {
                    cancel();
                    return;
                }

                if (player.getGameMode() != GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SPECTATOR);
                    Location sky = game.getSkyLocation();
                    if (sky != null) {
                        player.teleport(sky);
                    }
                }

                if (!redrop) {
                    if (++ticks >= SPECTATOR_GUARD_TICKS) {
                        cancel();
                    }
                    return;
                }

                if (ticks % 20 == 0) {
                    int remaining = REDROP_DELAY_SECONDS - ticks / 20;
                    if (remaining <= 0) {
                        cancel();
                        redrop(player);
                        return;
                    }
                    player.showTitle(Title.title(
                            LEGACY.deserialize(Lang.get("death.redrop-title", remaining)),
                            LEGACY.deserialize(Lang.get("death.redrop-subtitle")),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void redrop(Player player) {
        Location sky = game.getSkyLocation();
        if (sky != null) {
            player.teleport(sky);
        }

        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0f);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        // Der Tod hat alle Effekte gelöscht - in der Farmzeit gibt es die
        // Nachtsicht deshalb noch einmal.
        if (game.getPhase() == VaroGame.Phase.FARM) {
            nightVision.apply(player);
        }
        glideManager.startGlide(player);
    }

    /**
     * Die Runde endet, wenn nur noch eine "Partei" übrig ist - also ein Team
     * oder ein einzelner Spieler ohne Team. Teamlose zählen dabei jeder für
     * sich. Gibt true zurück, wenn die Runde damit beendet wurde.
     */
    private boolean checkWinner() {
        Set<Object> parties = new HashSet<>();
        for (UUID uuid : game.getAlive()) {
            VaroTeam team = game.getTeam(uuid);
            parties.add(team != null ? team : uuid);
        }

        if (parties.size() > 1) {
            return false;
        }

        if (parties.isEmpty()) {
            game.end(null);
            announce(Lang.get("win.nobody-left"));
            return true;
        }

        Object party = parties.iterator().next();
        Set<UUID> winners = new HashSet<>();
        String winnerName;
        VaroTeam winningTeam = null;

        if (party instanceof VaroTeam team) {
            winningTeam = team;
            winnerName = team.getLegacyColor() + team.getName();
            for (UUID uuid : game.getAlive()) {
                if (team.getMembers().contains(uuid)) {
                    winners.add(uuid);
                }
            }
        } else {
            UUID uuid = (UUID) party;
            winnerName = Bukkit.getOfflinePlayer(uuid).getName();
            winners.add(uuid);
        }

        game.end(winnerName);
        celebration.celebrate(winners, winnerName, winningTeam);
        return true;
    }

    private boolean isRunning() {
        return game.getPhase() == VaroGame.Phase.FARM || game.getPhase() == VaroGame.Phase.SHRINK;
    }

    private void announce(String message) {
        ArenaState arena = game.getArena();
        World world = arena == null ? null : Bukkit.getWorld(arena.worldName);
        if (world == null) {
            return;
        }

        for (Player player : world.getPlayers()) {
            player.sendMessage(message);
        }
    }
}
