package de.kato.varo.commands;

import de.kato.varo.ArenaState;
import de.kato.varo.Cage;
import de.kato.varo.Lang;
import de.kato.varo.UsedLocations;
import de.kato.varo.VaroGame;
import de.kato.varo.VaroSettings;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/**
 * /varosetup [BorderGröße] - baut eine neue Arena in der konfigurierten
 * Varo-Welt auf. Funktioniert von überall aus (auch von der Konsole oder
 * über einen NPC am Spawn), die Zielwelt kommt immer aus der config.yml.
 */
public class VaroSetupCommand implements CommandExecutor {

    // Y-Höhe des Käfigs
    private static final int CAGE_Y = 200;
    // Radius des Käfigs (Innenmaß = 2*RADIUS + 1, also 4 -> 9x9 Blöcke)
    private static final int CAGE_RADIUS = 4;
    // Innenhöhe des Käfigs
    private static final int CAGE_HEIGHT = 5;

    private static final int MIN_DISTANCE = 10_000;
    private static final int MAX_DISTANCE = 50_000;
    private static final int MAX_ATTEMPTS = 40;
    private static final int SEA_LEVEL = 63;

    // Biome ohne Bäume/Ressourcen oder komplett im Wasser. Abgeglichen wird
    // gegen den Biom-Schlüssel (z.B. "deep_frozen_ocean"), damit die Liste
    // auch nach Minecraft-Updates ohne Anpassung funktioniert.
    private static final String[] BLOCKED_BIOMES = {
            "ocean", "river", "beach", "desert", "badlands",
            "frozen", "ice_spikes", "mushroom_fields", "stony_shore"
    };

    private final JavaPlugin plugin;
    private final VaroGame game;
    private final VaroSettings settings;
    private final UsedLocations usedLocations;
    private final Random random = new Random();

    public VaroSetupCommand(JavaPlugin plugin, VaroGame game, VaroSettings settings,
                            UsedLocations usedLocations) {
        this.plugin = plugin;
        this.game = game;
        this.settings = settings;
        this.usedLocations = usedLocations;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int borderSize = settings.getBorderSize();

        if (args.length >= 1) {
            try {
                borderSize = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Lang.get("setup.size-not-a-number"));
                return true;
            }
        }

        createArena(sender, borderSize);
        return true;
    }

    /** Auch vom /varo-Menü aus aufgerufen. */
    public void createArena(CommandSender sender, int borderSize) {
        String arenaWorld = plugin.getConfig().getString("arena-world", "varo");

        World world = Bukkit.getWorld(arenaWorld);
        if (world == null) {
            sender.sendMessage(Lang.get("setup.arena-world-missing", arenaWorld));
            sender.sendMessage(Lang.get("setup.arena-world-hint", arenaWorld));
            return;
        }

        sender.sendMessage(Lang.get("setup.searching"));
        searchLocation(sender, world, borderSize, MAX_ATTEMPTS);
    }

    /**
     * Würfelt so lange Koordinaten, bis ein brauchbarer Ort gefunden ist:
     * Land über dem Meeresspiegel, kein ödes Biom und weit genug weg von
     * allen früheren Arenen.
     *
     * Die Chunks werden dabei asynchron erzeugt - würde man sie direkt im
     * Hauptthread anfassen, würde der Server bei jedem Versuch kurz einfrieren.
     */
    private void searchLocation(CommandSender sender, World world, int borderSize, int attemptsLeft) {
        if (attemptsLeft <= 0) {
            sender.sendMessage(Lang.get("setup.no-location"));
            return;
        }

        int centerX = randomCoordinate();
        int centerZ = randomCoordinate();

        // Mit der Bordergröße als Mindestabstand können sich zwei Spielfelder
        // nicht überschneiden - dieselbe Gegend kommt also nie wieder dran.
        if (usedLocations.isTooClose(centerX, centerZ, borderSize)) {
            searchLocation(sender, world, borderSize, attemptsLeft - 1);
            return;
        }

        world.getChunkAtAsync(centerX >> 4, centerZ >> 4).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (isSuitable(world, centerX, centerZ)) {
                        buildArena(sender, world, borderSize, centerX, centerZ);
                    } else {
                        searchLocation(sender, world, borderSize, attemptsLeft - 1);
                    }
                }));
    }

    private boolean isSuitable(World world, int centerX, int centerZ) {
        Block surface = world.getHighestBlockAt(centerX, centerZ);

        if (surface.getY() <= SEA_LEVEL || isWater(surface.getType())) {
            return false;
        }

        // Über Keyed statt direkt über Biome: Biome war bis 1.21.2 eine Enum und
        // ist seit 1.21.3 ein Interface - der Aufruf über die gemeinsame
        // Schnittstelle funktioniert auf beiden Seiten dieser Grenze.
        Keyed biomeKey = world.getBiome(centerX, surface.getY(), centerZ);
        String biome = biomeKey.getKey().getKey();
        for (String blocked : BLOCKED_BIOMES) {
            if (biome.contains(blocked)) {
                return false;
            }
        }
        return true;
    }

    private boolean isWater(Material material) {
        return material == Material.WATER
                || material == Material.ICE
                || material == Material.PACKED_ICE
                || material == Material.BLUE_ICE;
    }

    /** Gleichverteilt in beide Richtungen, MIN_DISTANCE..MAX_DISTANCE vom Ursprung. */
    private int randomCoordinate() {
        int distance = random.nextInt(MAX_DISTANCE - MIN_DISTANCE) + MIN_DISTANCE;
        return random.nextBoolean() ? distance : -distance;
    }

    private void buildArena(CommandSender sender, World world, int borderSize, int centerX, int centerZ) {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(borderSize);

        ArenaState state = new ArenaState();
        state.worldName = world.getName();
        state.centerX = centerX;
        state.centerZ = centerZ;
        state.cageY = CAGE_Y;
        state.cageRadius = CAGE_RADIUS;
        state.cageHeight = CAGE_HEIGHT;
        state.cageActive = true;

        Cage.build(world, state);
        world.setSpawnLocation(centerX, CAGE_Y + 1, centerZ);
        state.save(plugin);

        // Neue Runde: Lobby öffnen, alte Teilnehmer/Einladungen verwerfen.
        game.setArena(state);
        game.openLobby();
        usedLocations.add(centerX, centerZ);

        // Gelände rund um die Arena schon mal generieren lassen. Ohne das
        // schaut man an frischen Zufallskoordinaten beim Drop ins Schwarze.
        preloadChunks(world, centerX, centerZ);

        if (settings.getStartTime() >= 0) {
            world.setTime(settings.getStartTime());
        }

        // Die Border springt an eine neue, weit entfernte Position. Wer jetzt
        // woanders in dieser Welt steht, wäre schlagartig außerhalb - deshalb
        // alle in den neuen Käfig holen.
        Location cage = new Location(world, centerX + 0.5, CAGE_Y + 1, centerZ + 0.5);
        for (Player online : world.getPlayers()) {
            // Zuschauer aus der letzten Runde wieder spielfähig machen.
            if (online.getGameMode() == GameMode.SPECTATOR) {
                online.setGameMode(GameMode.ADVENTURE);
            }
            online.teleport(cage);
        }

        // Wer den Aufbau ausgelöst hat, landet ebenfalls in der Lobby -
        // auch wenn er dafür am Spawn einen NPC angeklickt hat.
        if (sender instanceof Player player && !world.equals(player.getWorld())) {
            player.teleport(cage);
        }

        sender.sendMessage(Lang.get("setup.created", world.getName()));
        sender.sendMessage(Lang.get("setup.center", centerX, centerZ, usedLocations.size()));
        sender.sendMessage(Lang.get("setup.border-size", borderSize));
        sender.sendMessage(Lang.get("setup.lobby-open"));
    }

    /** Erzeugt die Chunks rund um das Arena-Zentrum asynchron im Hintergrund. */
    private void preloadChunks(World world, int centerX, int centerZ) {
        int radius = settings.getPreloadRadius();
        int centerChunkX = centerX >> 4;
        int centerChunkZ = centerZ >> 4;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getChunkAtAsync(centerChunkX + x, centerChunkZ + z);
            }
        }
    }
}
