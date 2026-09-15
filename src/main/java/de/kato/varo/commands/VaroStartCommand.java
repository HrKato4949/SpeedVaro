package de.kato.varo.commands;

import de.kato.varo.ArenaState;
import de.kato.varo.Cage;
import de.kato.varo.Lang;
import de.kato.varo.VaroElevator;
import de.kato.varo.VaroGame;
import de.kato.varo.VaroGlideManager;
import de.kato.varo.VaroNightVision;
import de.kato.varo.VaroSettings;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.UUID;

/**
 * /varostart [farmMinuten] [zielGröße] [schrumpfSekunden]
 *
 * Phase 3 des Varo-Turniers:
 * - entfernt den Glaskäfig
 * - versetzt alle angemeldeten Teilnehmer in einen kontrollierten Gleitflug
 *   (Fortnite-Style, Steuerung per Blickrichtung) - siehe VaroGlideManager
 * - lässt nach der Farmzeit die Worldborder stufenlos schrumpfen
 *
 * Fallschaden beim Landen fängt der VaroProtectionListener ab, weil in der
 * Farmzeit ohnehin kein Fallschaden gilt.
 */
public class VaroStartCommand implements CommandExecutor {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final VaroGame game;
    private final VaroSettings settings;
    private final VaroGlideManager glideManager;
    private final VaroElevator elevator;
    private final VaroNightVision nightVision;

    /** Laufender Countdown - verhindert einen zweiten Start währenddessen. */
    private BukkitTask countdownTask;

    public VaroStartCommand(JavaPlugin plugin, VaroGame game, VaroSettings settings,
                            VaroGlideManager glideManager, VaroElevator elevator,
                            VaroNightVision nightVision) {
        this.plugin = plugin;
        this.game = game;
        this.settings = settings;
        this.glideManager = glideManager;
        this.elevator = elevator;
        this.nightVision = nightVision;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int farmMinutes = settings.getFarmMinutes();
        double targetSize = settings.getTargetSize();
        int shrinkSeconds = settings.getShrinkMinutes() * 60;

        try {
            if (args.length >= 1) farmMinutes = Integer.parseInt(args[0]);
            if (args.length >= 2) targetSize = Double.parseDouble(args[1]);
            if (args.length >= 3) shrinkSeconds = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Lang.get("start.usage"));
            return true;
        }

        startRound(sender, farmMinutes, targetSize, shrinkSeconds);
        return true;
    }

    /** Auch vom /varo-Menü aus aufgerufen. */
    public void startRound(CommandSender sender, int farmMinutes, double targetSize, int shrinkSeconds) {

        ArenaState arena = game.getArena();
        if (arena == null) {
            sender.sendMessage(Lang.get("start.no-arena"));
            return;
        }

        if (game.getPhase() != VaroGame.Phase.LOBBY) {
            sender.sendMessage(Lang.get("start.already-running"));
            return;
        }

        if (game.getParticipants().isEmpty()) {
            sender.sendMessage(Lang.get("start.no-participants"));
            return;
        }

        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) {
            sender.sendMessage(Lang.get("general.world-missing", arena.worldName));
            return;
        }

        if (countdownTask != null) {
            sender.sendMessage(Lang.get("start.countdown-running"));
            return;
        }

        int countdown = settings.getCountdownSeconds();
        if (countdown <= 0) {
            drop(sender, world, arena, farmMinutes, targetSize, shrinkSeconds);
            return;
        }

        sender.sendMessage(Lang.get("start.countdown-started", countdown));
        countdownTask = new BukkitRunnable() {
            private int remaining = countdown;

            @Override
            public void run() {
                // Wurde die Lobby zwischendurch zurückgesetzt, still abbrechen.
                if (game.getPhase() != VaroGame.Phase.LOBBY) {
                    stopCountdown();
                    return;
                }

                if (remaining > 0) {
                    showCountdown(world, remaining);
                    remaining--;
                    return;
                }

                stopCountdown();
                drop(sender, world, arena, farmMinutes, targetSize, shrinkSeconds);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void stopCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    /** Große Zahl in der Bildschirmmitte plus Ton für alle in der Varo-Welt. */
    private void showCountdown(World world, int seconds) {
        String key = seconds <= 3 ? "start.countdown-title-final" : "start.countdown-title";
        Title title = Title.title(
                LEGACY.deserialize(Lang.get(key, seconds)),
                LEGACY.deserialize(Lang.get("start.countdown-subtitle")),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100)));

        float pitch = seconds <= 3 ? 1.6f : 1.0f;
        Sound tick = Sound.sound(Key.key("block.note_block.pling"), Sound.Source.MASTER, 1f, pitch);

        for (Player player : world.getPlayers()) {
            player.showTitle(title);
            player.playSound(tick);
        }
    }

    private void drop(CommandSender sender, World world, ArenaState arena,
                      int farmMinutes, double targetSize, int shrinkSeconds) {

        Title go = Title.title(
                LEGACY.deserialize(Lang.get("start.go-title")),
                LEGACY.deserialize(Lang.get("start.go-subtitle")),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500)));
        Sound launch = Sound.sound(Key.key("entity.firework_rocket.launch"), Sound.Source.MASTER, 1f, 1f);
        for (Player player : world.getPlayers()) {
            player.showTitle(go);
            player.playSound(launch);
        }

        // 1. Käfig entfernen (Wände, Boden, Decke -> Luft)
        Cage.clear(world, arena);

        // Käfig ist weg - das Join-Sicherheitsnetz darf ab jetzt niemanden
        // mehr dorthin teleportieren.
        arena.cageActive = false;
        arena.save(plugin);

        // Der Weltspawn zeigt noch auf den aufgelösten Käfig in der Luft.
        // Bleibt er so, verweigert z.B. Multiverse später den Teleport in
        // diese Welt ("location is deemed unsafe"). Deshalb auf den Boden
        // unter dem Arena-Zentrum umlegen.
        world.setSpawnLocation(arena.centerX,
                world.getHighestBlockYAt(arena.centerX, arena.centerZ) + 1,
                arena.centerZ);

        // Beim Drop soll man auch etwas sehen - eingestellte Tageszeit setzen.
        if (settings.getStartTime() >= 0) {
            world.setTime(settings.getStartTime());
        }

        // 2. Nur die angemeldeten Teilnehmer in den Sturzflug schicken
        int dropped = 0;
        for (UUID uuid : game.getParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                startDrop(player);
                dropped++;
            }
        }

        // 3. Phase auf Farmzeit umstellen (füttert Scoreboard und /varo-Menü)
        game.startFarm(farmMinutes, targetSize, settings.getLives());
        for (String line : Lang.list("start.drop-announce")) {
            announce(world, line);
        }

        // 4. Nach der Farmzeit beginnt die Border zu schrumpfen
        int finalShrinkSeconds = shrinkSeconds;
        double finalTargetSize = targetSize;
        new BukkitRunnable() {
            @Override
            public void run() {
                WorldBorder border = world.getWorldBorder();
                // setSize(größe, sekunden) animiert das Schrumpfen stufenlos
                // über die angegebene Dauer, statt es sofort zu setzen.
                border.setSize(finalTargetSize, finalShrinkSeconds);
                game.startShrink(finalShrinkSeconds);
                nightVision.removeAll(world);
                announce(world, Lang.get("start.shrink-announce"));
            }
        }.runTaskLater(plugin, farmMinutes * 60L * 20L);

        sender.sendMessage(Lang.get("start.started", dropped));
        sender.sendMessage(Lang.get("start.shrink-in", farmMinutes));
    }

    /** Nachricht an alle Spieler in der Varo-Welt (statt serverweitem Broadcast). */
    private void announce(World world, String message) {
        for (Player player : world.getPlayers()) {
            player.sendMessage(message);
        }
    }

    /** Versetzt einen Spieler in den Sturzflug: Survival-Modus, satt, Startitem, Gleiten. */
    private void startDrop(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0f);
        // Fairer Start für alle - die Farmzeit friert den Balken danach ein.
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.getInventory().addItem(elevator.createItem());
        nightVision.apply(player);
        glideManager.startGlide(player);
    }

}