package de.kato.varo;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

import de.kato.varo.commands.VaroAcceptCommand;
import de.kato.varo.commands.VaroBackpackCommand;
import de.kato.varo.commands.VaroCancelCommand;
import de.kato.varo.commands.VaroLeaveCommand;
import de.kato.varo.commands.VaroMenuCommand;
import de.kato.varo.commands.VaroResetCommand;
import de.kato.varo.commands.VaroSafenetCommand;
import de.kato.varo.commands.VaroSetupCommand;
import de.kato.varo.commands.VaroSpectateCommand;
import de.kato.varo.commands.VaroStartCommand;
import de.kato.varo.gui.VaroGui;
import de.kato.varo.listeners.VaroCommandListener;
import de.kato.varo.listeners.VaroDeathListener;
import de.kato.varo.listeners.VaroHarvestListener;
import de.kato.varo.listeners.VaroInventoryListener;
import de.kato.varo.listeners.VaroJoinListener;
import de.kato.varo.listeners.VaroProtectionListener;
import de.kato.varo.listeners.VaroSmeltListener;
import de.kato.varo.listeners.VaroTeammateListener;
import de.kato.varo.listeners.VaroToolListener;
import de.kato.varo.listeners.VaroWorkstationListener;
import de.kato.varo.scoreboard.VaroScoreboardManager;

public final class VaroMain extends JavaPlugin {

    private VaroScoreboardManager scoreboardManager;
    private VaroResourcePack resourcePack;
    private VaroGlideManager glideManager;
    private VaroCommandListener commandListener;
    private final List<BlockPopulator> populators = new ArrayList<>();

    @Override
    public void onEnable() {
        // Neue Schlüssel aus der mitgelieferten config.yml in eine bereits
        // vorhandene Datei nachziehen, statt nur beim allerersten Start zu schreiben.
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        Lang.load(this);

        VaroGame game = new VaroGame();

        // Nach einem Neustart die gespeicherte Arena übernehmen. Steht der
        // Käfig noch, befinden wir uns weiterhin in der Lobby-Phase.
        ArenaState arena = ArenaState.load(this);
        if (arena != null) {
            game.setArena(arena);
            if (arena.cageActive) {
                game.openLobby();
            }
        }

        VaroSettings settings = new VaroSettings(this);
        VaroJoinListener joinListener = new VaroJoinListener(game);
        VaroInventoryListener inventoryListener = new VaroInventoryListener(this, game);
        resourcePack = new VaroResourcePack(this);
        scoreboardManager = new VaroScoreboardManager(this, game, resourcePack);
        glideManager = new VaroGlideManager(this);
        VaroElevator elevator = new VaroElevator(this, game);
        VaroNightVision nightVision = new VaroNightVision(this, game);

        VaroSetupCommand setupCommand = new VaroSetupCommand(this, game, settings, new UsedLocations(this));
        VaroStartCommand startCommand = new VaroStartCommand(this, game, settings, glideManager, elevator,
                nightVision);
        VaroResetCommand resetCommand = new VaroResetCommand(this, game);
        VaroBackpackCommand backpackCommand = new VaroBackpackCommand(game);
        VaroAcceptCommand acceptCommand = new VaroAcceptCommand(game);
        VaroDeathChest deathChest = new VaroDeathChest(this);
        VaroDeathListener deathListener = new VaroDeathListener(this, game, glideManager,
                new VaroCelebration(this, game), deathChest, nightVision);
        VaroGui gui = new VaroGui(this, game, settings, setupCommand, startCommand, resetCommand,
                backpackCommand, acceptCommand, joinListener, deathListener, resourcePack);

        getServer().getPluginManager().registerEvents(joinListener, this);
        getServer().getPluginManager().registerEvents(inventoryListener, this);
        getServer().getPluginManager().registerEvents(deathListener, this);
        getServer().getPluginManager().registerEvents(deathChest, this);
        getServer().getPluginManager().registerEvents(new VaroProtectionListener(this, game), this);
        getServer().getPluginManager().registerEvents(nightVision, this);
        commandListener = new VaroCommandListener(this, game);
        getServer().getPluginManager().registerEvents(commandListener, this);
        getServer().getPluginManager().registerEvents(new VaroToolListener(game), this);
        getServer().getPluginManager().registerEvents(new VaroHarvestListener(game), this);
        getServer().getPluginManager().registerEvents(new VaroSmeltListener(game), this);
        getServer().getPluginManager().registerEvents(new VaroTeammateListener(game), this);
        getServer().getPluginManager().registerEvents(elevator, this);
        registerPopulators();

        VaroWorkstationListener workstations = new VaroWorkstationListener(game);
        getServer().getPluginManager().registerEvents(workstations, this);
        getCommand("varoanvil").setExecutor(workstations);
        getCommand("varoenchant").setExecutor(workstations);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(scoreboardManager, this);
        getServer().getPluginManager().registerEvents(glideManager, this);
        getServer().getPluginManager().registerEvents(resourcePack, this);
        resourcePack.load();
        scoreboardManager.start();
        glideManager.start();

        getCommand("varosetup").setExecutor(setupCommand);
        getCommand("varoreset").setExecutor(resetCommand);
        getCommand("varostart").setExecutor(startCommand);
        getCommand("varosafenet").setExecutor(new VaroSafenetCommand(joinListener));
        getCommand("varo").setExecutor(new VaroMenuCommand(gui, this::reload, resourcePack));
        getCommand("varoleave").setExecutor(new VaroLeaveCommand(this, game));
        getCommand("varobackpack").setExecutor(backpackCommand);
        getCommand("varoaccept").setExecutor(acceptCommand);
        getCommand("varospec").setExecutor(new VaroSpectateCommand(gui));
        getCommand("varocancel").setExecutor(new VaroCancelCommand(game));

        Bukkit.getConsoleSender().sendMessage("§a[SpeedVaro] Version " + getDescription().getVersion()
                + " enabled (language: " + getConfig().getString("language", "en") + ").");

        VaroUpdateChecker updateChecker = new VaroUpdateChecker(this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        updateChecker.check();
    }

    /**
     * Hängt die Weltgenerierungs-Extras (Diamanten, Zuckerrohr) an die
     * Varo-Welt. Multiverse lädt Welten unter Umständen erst nach uns,
     * deshalb zusätzlich per WorldLoadEvent.
     */
    private void registerPopulators() {
        applyPopulators();

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onWorldLoad(WorldLoadEvent event) {
                World world = event.getWorld();
                if (world.getName().equals(getConfig().getString("arena-world", "varo"))
                        && !world.getPopulators().containsAll(populators)) {
                    world.getPopulators().addAll(populators);
                }
            }
        }, this);
    }

    /** Baut die Populatoren aus der Config neu auf; alte Instanzen werden vorher entfernt. */
    private void applyPopulators() {
        String arenaWorld = getConfig().getString("arena-world", "varo");
        World world = Bukkit.getWorld(arenaWorld);
        if (world != null) {
            world.getPopulators().removeAll(populators);
        }
        populators.clear();

        int veins = getConfig().getInt("extra-diamond-veins-per-chunk", 2);
        if (veins > 0) {
            populators.add(new VaroOrePopulator(veins));
        }
        if (getConfig().getBoolean("extra-sugar-cane", true)) {
            populators.add(new VaroSugarCanePopulator());
        }

        if (world != null) {
            world.getPopulators().addAll(populators);
        }
    }

    /** /varo reload: Config und Sprachdateien neu einlesen und alles nachziehen, was sie nur beim Start liest. */
    private void reload() {
        reloadConfig();
        Lang.load(this);
        commandListener.reload();
        resourcePack.load();
        applyPopulators();
    }

    @Override
    public void onDisable() {
        if (scoreboardManager != null) {
            scoreboardManager.stop();
        }
        if (glideManager != null) {
            glideManager.stop();
        }
        Bukkit.getConsoleSender().sendMessage("§c[SpeedVaro] Plugin disabled.");
    }
}
