package de.kato.varo.gui;

import de.kato.varo.ArenaState;
import de.kato.varo.VaroGame;
import de.kato.varo.VaroSettings;
import de.kato.varo.VaroTeam;
import de.kato.varo.commands.VaroAcceptCommand;
import de.kato.varo.commands.VaroBackpackCommand;
import de.kato.varo.commands.VaroResetCommand;
import de.kato.varo.commands.VaroSetupCommand;
import de.kato.varo.commands.VaroStartCommand;
import de.kato.varo.listeners.VaroDeathListener;
import de.kato.varo.listeners.VaroJoinListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Das komplette Varo-Menü hinter /varo: Beitreten/Verlassen, Teams, und für
 * Admins Arena aufbauen, Runde starten, Einstellungen und Zurücksetzen -
 * damit im Betrieb kein einziger Befehl mehr nötig ist.
 */
public class VaroGui implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // Hauptmenü
    private static final int SLOT_INFO = 4;
    private static final int SLOT_JOIN = 10;
    private static final int SLOT_TEAMS = 12;
    private static final int SLOT_INVITE = 14;
    private static final int SLOT_ADMIN = 16;
    private static final int SLOT_SPECTATE = 20;
    private static final int SLOT_BACKPACK = 22;

    // Team-Menü (Teams selbst liegen auf 0..MAX_TEAMS-1)
    private static final int SLOT_TEAM_BACK = 18;
    private static final int SLOT_TEAM_LEAVE = 20;
    private static final int SLOT_TEAM_CREATE = 22;

    // Admin-Menü
    private static final int SLOT_ADMIN_CREATE = 10;
    private static final int SLOT_ADMIN_START = 12;
    private static final int SLOT_ADMIN_SETTINGS = 14;
    private static final int SLOT_ADMIN_RESET = 16;
    private static final int SLOT_ADMIN_KICK = 20;
    private static final int SLOT_ADMIN_SAFENET = 22;
    private static final int SLOT_ADMIN_BACK = 18;

    // Einstellungen (36 Slots: zwei Reihen Werte, Zurück unten mittig)
    private static final int SLOT_SET_BORDER = 10;
    private static final int SLOT_SET_FARM = 12;
    private static final int SLOT_SET_TARGET = 14;
    private static final int SLOT_SET_SHRINK = 16;
    private static final int SLOT_SET_LIVES = 19;
    private static final int SLOT_SET_COUNTDOWN = 21;
    private static final int SLOT_SET_CHUNKS = 23;
    private static final int SLOT_SET_TIME = 25;
    private static final int SLOT_SET_BACK = 31;

    /** Letzte Reihe der Einladen-Liste bleibt frei. */
    private static final int INVITE_CAPACITY = 45;

    private final JavaPlugin plugin;
    private final VaroGame game;
    private final VaroSettings settings;
    private final VaroSetupCommand setupCommand;
    private final VaroStartCommand startCommand;
    private final VaroResetCommand resetCommand;
    private final VaroBackpackCommand backpackCommand;
    private final VaroAcceptCommand acceptCommand;
    private final VaroJoinListener joinListener;
    private final VaroDeathListener deathListener;

    public VaroGui(JavaPlugin plugin, VaroGame game, VaroSettings settings,
                   VaroSetupCommand setupCommand, VaroStartCommand startCommand,
                   VaroResetCommand resetCommand, VaroBackpackCommand backpackCommand,
                   VaroAcceptCommand acceptCommand, VaroJoinListener joinListener,
                   VaroDeathListener deathListener) {
        this.plugin = plugin;
        this.game = game;
        this.settings = settings;
        this.setupCommand = setupCommand;
        this.startCommand = startCommand;
        this.resetCommand = resetCommand;
        this.backpackCommand = backpackCommand;
        this.acceptCommand = acceptCommand;
        this.joinListener = joinListener;
        this.deathListener = deathListener;
    }

    // ------------------------------------------------------------------ Menüs

    public void openMain(Player player) {
        Inventory menu = createMenu(VaroMenuHolder.MenuType.MAIN, 27, "§6§lVARO");

        UUID uuid = player.getUniqueId();
        boolean lobby = game.getPhase() == VaroGame.Phase.LOBBY;

        if (game.isParticipant(uuid)) {
            menu.setItem(SLOT_JOIN, lobby
                    ? item(Material.BARRIER, "§cVaro verlassen", "§7Du bist angemeldet.")
                    : item(Material.LIME_DYE, "§aDu bist dabei", "§7Die Runde läuft bereits."));
        } else if (lobby && mayJoin(player)) {
            menu.setItem(SLOT_JOIN, item(Material.LIME_DYE, "§aVaro beitreten",
                    "§7Du wirst direkt in die Lobby teleportiert."));
        } else {
            menu.setItem(SLOT_JOIN, item(Material.GRAY_DYE, "§7Keine Einladung",
                    "§7Ein Admin muss dich einladen."));
        }

        VaroTeam ownTeam = game.getTeam(uuid);
        menu.setItem(SLOT_INFO, item(Material.BOOK, "§eStatus",
                "§7Phase: §f" + phaseName(),
                "§7Teilnehmer: §f" + game.getParticipants().size(),
                "§7Dein Team: " + (ownTeam == null ? "§7keins" : ownTeam.getLegacyColor() + ownTeam.getName())));

        menu.setItem(SLOT_TEAMS, item(Material.WHITE_BANNER, "§bTeams",
                "§7Team erstellen oder beitreten.",
                "§7Teams: §f" + game.getTeams().size() + "§7/§f" + VaroTeam.MAX_TEAMS));

        if (player.hasPermission("varo.invite")) {
            menu.setItem(SLOT_INVITE, item(Material.PLAYER_HEAD, "§aSpieler einladen",
                    "§7Holt Spieler direkt in die Lobby."));
        }

        if (player.hasPermission("varo.admin")) {
            menu.setItem(SLOT_ADMIN, item(Material.COMPARATOR, "§cAdmin",
                    "§7Arena, Runde und Einstellungen."));
        }

        if (ownTeam != null && game.isInActiveArena(player)) {
            menu.setItem(SLOT_BACKPACK, item(Material.CHEST, ownTeam.getLegacyColor() + "Team-Backpack",
                    "§7Gemeinsame Kiste für " + ownTeam.getLegacyColor() + ownTeam.getName() + "§7.",
                    "§8Auch per §7/backpack"));
        }

        if (maySpectate(player)) {
            menu.setItem(SLOT_SPECTATE, item(Material.COMPASS, "§bZuschauen",
                    "§7Teleportiert dich zu einem lebenden Spieler.",
                    "§8Auch per §7/spec"));
        }

        player.openInventory(menu);
    }

    public void openSpectate(Player spectator) {
        if (!maySpectate(spectator)) {
            spectator.sendMessage("§cZuschauen geht nur als Zuschauer in der Varo-Welt.");
            return;
        }

        Inventory menu = createMenu(VaroMenuHolder.MenuType.SPECTATE, 54, "§bZuschauen");

        int slot = 0;
        for (UUID uuid : game.getAlive()) {
            Player target = Bukkit.getPlayer(uuid);
            if (target == null || slot >= INVITE_CAPACITY) {
                continue;
            }

            VaroTeam team = game.getTeam(uuid);
            menu.setItem(slot++, head(target,
                    team == null ? "§7Kein Team" : "§7Team: " + team.getLegacyColor() + team.getName(),
                    "§7Leben: " + game.getLives(uuid) + "§7/§f" + game.getMaxLives(),
                    "§eKlicken zum Teleportieren"));
        }

        spectator.openInventory(menu);
    }

    public void openTeams(Player player) {
        Inventory menu = createMenu(VaroMenuHolder.MenuType.TEAMS, 27, "§6Teams");

        VaroTeam ownTeam = game.getTeam(player.getUniqueId());
        List<VaroTeam> teams = game.getTeams();

        for (int i = 0; i < teams.size(); i++) {
            VaroTeam team = teams.get(i);
            menu.setItem(i, item(team.getWool(), team.getLegacyColor() + "§l" + team.getName(),
                    "§7Mitglieder: §f" + team.getMembers().size(),
                    team == ownTeam ? "§aDu bist in diesem Team" : "§eKlicken zum Beitreten"));
        }

        if (teams.size() < VaroTeam.MAX_TEAMS) {
            menu.setItem(SLOT_TEAM_CREATE, item(Material.NETHER_STAR, "§aNeues Team erstellen",
                    "§7Legt §fTeam " + (teams.size() + 1) + "§7 an."));
        }

        if (ownTeam != null) {
            menu.setItem(SLOT_TEAM_LEAVE, item(Material.BARRIER, "§cTeam verlassen",
                    "§7Du spielst dann alleine."));
        }

        menu.setItem(SLOT_TEAM_BACK, item(Material.ARROW, "§7Zurück"));
        player.openInventory(menu);
    }

    public void openInvite(Player admin) {
        Inventory menu = createMenu(VaroMenuHolder.MenuType.INVITE, 54, "§bSpieler einladen");

        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= INVITE_CAPACITY) {
                break;
            }
            if (game.isParticipant(online.getUniqueId())) {
                continue;
            }
            menu.setItem(slot++, head(online, game.isInvited(online.getUniqueId())
                    ? "§7Bereits eingeladen"
                    : "§eKlicken zum Einladen"));
        }

        admin.openInventory(menu);
    }

    public void openAdmin(Player admin) {
        Inventory menu = createMenu(VaroMenuHolder.MenuType.ADMIN, 27, "§cVaro-Admin");

        ArenaState arena = game.getArena();
        menu.setItem(SLOT_ADMIN_CREATE, item(Material.EMERALD_BLOCK, "§aArena aufbauen",
                "§7Sucht einen neuen Ort und öffnet die Lobby.",
                "§7Border: §f" + settings.getBorderSize(),
                arena == null ? "§8Noch keine Arena" : "§8Aktuell: " + arena.centerX + " / " + arena.centerZ));

        menu.setItem(SLOT_ADMIN_START, item(Material.FIREWORK_ROCKET, "§6Runde starten",
                "§7Löst den Drop aus.",
                "§7Farmzeit: §f" + settings.getFarmMinutes() + " min",
                "§7Ziel-Border: §f" + settings.getTargetSize(),
                "§7Schrumpfdauer: §f" + settings.getShrinkMinutes() + " min",
                "§7Countdown: §f" + settings.getCountdownSeconds() + " s",
                "§7Angemeldet: §f" + game.getParticipants().size()));

        menu.setItem(SLOT_ADMIN_SETTINGS, item(Material.REPEATER, "§eEinstellungen",
                "§7Werte für die nächste Runde."));

        menu.setItem(SLOT_ADMIN_RESET, item(Material.TNT, "§cWelt zurücksetzen",
                "§7Border auf Maximum, Weltspawn sichern,",
                "§7Käfigreste entfernen."));

        menu.setItem(SLOT_ADMIN_KICK, item(Material.IRON_DOOR, "§cSpieler entfernen",
                "§7Wirft jemanden aus der Runde - z.B. wer",
                "§7offline ging und den Sieg blockiert.",
                "§7Angemeldet: §f" + game.getParticipants().size()));

        menu.setItem(SLOT_ADMIN_SAFENET, item(
                joinListener.isEnabled() ? Material.SHIELD : Material.GRAY_DYE,
                "§bJoin-Sicherheitsnetz " + (joinListener.isEnabled() ? "§aan" : "§caus"),
                "§7Teleportiert joinende Spieler in den Käfig,",
                "§7solange die Lobby steht.",
                "§eKlicken zum Umschalten"));

        menu.setItem(SLOT_ADMIN_BACK, item(Material.ARROW, "§7Zurück"));
        admin.openInventory(menu);
    }

    public void openKick(Player admin) {
        Inventory menu = createMenu(VaroMenuHolder.MenuType.KICK, 54, "§cSpieler entfernen");

        int slot = 0;
        for (UUID uuid : game.getParticipants()) {
            if (slot >= INVITE_CAPACITY) {
                break;
            }

            OfflinePlayer participant = Bukkit.getOfflinePlayer(uuid);
            boolean alive = game.getPhase() == VaroGame.Phase.LOBBY || game.isAlive(uuid);
            menu.setItem(slot++, head(participant,
                    participant.isOnline() ? "§aOnline" : "§cOffline",
                    alive ? "§7Noch im Spiel" : "§8Bereits ausgeschieden",
                    "§cKlicken zum Entfernen"));
        }

        menu.setItem(INVITE_CAPACITY + 4, item(Material.ARROW, "§7Zurück"));
        admin.openInventory(menu);
    }

    public void openSettings(Player admin) {
        Inventory menu = createMenu(VaroMenuHolder.MenuType.SETTINGS, 36, "§eEinstellungen");

        menu.setItem(SLOT_SET_LIVES, item(Material.TOTEM_OF_UNDYING, "§aRespawns",
                "§7Aktuell: " + (settings.getLives() == 0 ? "§7keine" : "§b" + "❤".repeat(settings.getLives())),
                "§7Jedes Herz ist ein Respawn von oben.",
                "§7Erst der Tod ohne Herz ist endgültig.",
                "§8Linksklick §7+1 §8| §8Rechtsklick §7-1"));

        menu.setItem(SLOT_SET_BORDER, item(Material.MAP, "§aBorder-Größe",
                "§7Aktuell: §f" + settings.getBorderSize(),
                "§8Linksklick §7+250 §8| §8Rechtsklick §7-250"));

        menu.setItem(SLOT_SET_FARM, item(Material.CLOCK, "§aFarmzeit",
                "§7Aktuell: §f" + settings.getFarmMinutes() + " Minuten",
                "§8Linksklick §7+5 §8| §8Rechtsklick §7-5"));

        menu.setItem(SLOT_SET_TARGET, item(Material.TARGET, "§aZiel-Border",
                "§7Aktuell: §f" + settings.getTargetSize(),
                "§7Darauf schrumpft die Border.",
                "§8Linksklick §7+100 §8| §8Rechtsklick §7-100"));

        menu.setItem(SLOT_SET_SHRINK, item(Material.PISTON, "§aSchrumpfdauer",
                "§7Aktuell: §f" + settings.getShrinkMinutes() + " Minuten",
                "§8Linksklick §7+5 §8| §8Rechtsklick §7-5"));

        menu.setItem(SLOT_SET_CHUNKS, item(Material.GRASS_BLOCK, "§aChunk-Vorgenerierung",
                "§7Radius: §f" + settings.getPreloadRadius() + " Chunks",
                "§7Erzeugt §f" + settings.getPreloadChunkCount() + "§7 Chunks beim Aufbau.",
                "§8Größer = weniger schwarzes Gelände beim Drop.",
                "§8Linksklick §7+2 §8| §8Rechtsklick §7-2"));

        menu.setItem(SLOT_SET_TIME, item(Material.DAYLIGHT_DETECTOR, "§aStartzeit",
                "§7Aktuell: §f" + settings.getStartTimeName(),
                "§7Weltzeit beim Aufbau und beim Drop.",
                "§8Klicken zum Umschalten"));

        menu.setItem(SLOT_SET_COUNTDOWN, item(Material.BELL, "§aCountdown",
                "§7Aktuell: §f" + settings.getCountdownSeconds() + " Sekunden",
                "§7Vorlauf mit Anzeige, bevor der Käfig aufgeht.",
                "§80 = sofort starten",
                "§8Linksklick §7+1 §8| §8Rechtsklick §7-1"));

        menu.setItem(SLOT_SET_BACK, item(Material.ARROW, "§7Zurück"));
        admin.openInventory(menu);
    }

    // ------------------------------------------------------------------ Klicks

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof VaroMenuHolder holder)) {
            return;
        }

        // Unsere Menüs sind reine Anzeige - nichts darf herausgenommen werden.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        switch (holder.getType()) {
            case MAIN -> handleMainClick(player, slot);
            case INVITE -> handleInviteClick(player, event.getCurrentItem());
            case TEAMS -> handleTeamsClick(player, slot);
            case ADMIN -> handleAdminClick(player, slot);
            case SETTINGS -> handleSettingsClick(player, slot, event.isRightClick());
            case SPECTATE -> handleSpectateClick(player, event.getCurrentItem());
            case KICK -> handleKickClick(player, slot, event.getCurrentItem());
        }
    }

    private void handleSpectateClick(Player spectator, ItemStack clicked) {
        if (!maySpectate(spectator)
                || clicked == null || !(clicked.getItemMeta() instanceof SkullMeta skull)) {
            return;
        }

        OfflinePlayer owner = skull.getOwningPlayer();
        Player target = owner == null ? null : owner.getPlayer();
        if (target == null || !game.isAlive(target.getUniqueId())) {
            spectator.sendMessage("§cDieser Spieler ist nicht mehr im Spiel.");
            openSpectate(spectator);
            return;
        }

        spectator.closeInventory();
        spectator.teleport(target.getLocation());
        spectator.sendMessage("§7Du schaust jetzt §f" + target.getName() + "§7 zu.");
    }

    private void handleKickClick(Player admin, int slot, ItemStack clicked) {
        if (!admin.hasPermission("varo.admin")) {
            return;
        }

        if (slot == INVITE_CAPACITY + 4) {
            openAdmin(admin);
            return;
        }

        if (clicked == null || !(clicked.getItemMeta() instanceof SkullMeta skull)) {
            return;
        }

        OfflinePlayer target = skull.getOwningPlayer();
        if (target == null || !game.isParticipant(target.getUniqueId())) {
            openKick(admin);
            return;
        }

        deathListener.kick(target.getUniqueId());
        admin.sendMessage("§c" + target.getName() + " §7wurde aus der Runde entfernt.");
        openKick(admin);
    }

    private void handleMainClick(Player player, int slot) {
        if (slot == SLOT_TEAMS) {
            openTeams(player);
            return;
        }

        if (slot == SLOT_INVITE) {
            if (player.hasPermission("varo.invite")) {
                openInvite(player);
            }
            return;
        }

        if (slot == SLOT_ADMIN) {
            if (player.hasPermission("varo.admin")) {
                openAdmin(player);
            }
            return;
        }

        if (slot == SLOT_BACKPACK) {
            backpackCommand.openBackpack(player);
            return;
        }

        if (slot == SLOT_SPECTATE) {
            openSpectate(player);
            return;
        }

        if (slot != SLOT_JOIN || game.getPhase() != VaroGame.Phase.LOBBY) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (game.isParticipant(uuid)) {
            game.removeParticipant(uuid);
            sendToLobbyWorld(player);
            player.sendMessage("§cDu hast die Varo-Runde verlassen.");
        } else if (mayJoin(player)) {
            acceptCommand.accept(player);
        } else {
            return;
        }

        openMain(player);
    }

    private void handleTeamsClick(Player player, int slot) {
        if (slot == SLOT_TEAM_BACK) {
            openMain(player);
            return;
        }

        // Teams lassen sich nur in der Lobby umbauen - mitten in der Runde
        // würde ein Wechsel die Sieg-Erkennung durcheinanderbringen.
        if (game.getPhase() != VaroGame.Phase.LOBBY) {
            player.sendMessage("§cTeams können nur in der Lobby geändert werden.");
            return;
        }

        UUID uuid = player.getUniqueId();

        if (slot == SLOT_TEAM_CREATE) {
            VaroTeam created = game.createTeam();
            if (created == null) {
                player.sendMessage("§cEs gibt bereits die maximale Anzahl an Teams.");
                return;
            }
            game.joinTeam(uuid, created);
            player.sendMessage("§a" + created.getLegacyColor() + created.getName() + " §aerstellt - du bist beigetreten.");
            openTeams(player);
            return;
        }

        if (slot == SLOT_TEAM_LEAVE) {
            game.leaveTeam(uuid);
            player.sendMessage("§cDu hast dein Team verlassen.");
            openTeams(player);
            return;
        }

        List<VaroTeam> teams = game.getTeams();
        if (slot >= 0 && slot < teams.size()) {
            VaroTeam team = teams.get(slot);
            game.joinTeam(uuid, team);
            player.sendMessage("§aDu bist " + team.getLegacyColor() + team.getName() + "§a beigetreten.");
            openTeams(player);
        }
    }

    private void handleAdminClick(Player admin, int slot) {
        if (!admin.hasPermission("varo.admin")) {
            return;
        }

        switch (slot) {
            case SLOT_ADMIN_BACK -> openMain(admin);
            case SLOT_ADMIN_SETTINGS -> openSettings(admin);
            case SLOT_ADMIN_KICK -> openKick(admin);
            case SLOT_ADMIN_CREATE -> {
                admin.closeInventory();
                setupCommand.createArena(admin, settings.getBorderSize());
            }
            case SLOT_ADMIN_START -> {
                admin.closeInventory();
                startCommand.startRound(admin, settings.getFarmMinutes(),
                        settings.getTargetSize(), settings.getShrinkMinutes() * 60);
            }
            case SLOT_ADMIN_RESET -> {
                admin.closeInventory();
                resetCommand.resetWorld(admin);
            }
            case SLOT_ADMIN_SAFENET -> {
                joinListener.setEnabled(!joinListener.isEnabled());
                openAdmin(admin);
            }
            default -> { }
        }
    }

    private void handleSettingsClick(Player admin, int slot, boolean rightClick) {
        if (!admin.hasPermission("varo.admin")) {
            return;
        }

        if (slot == SLOT_SET_BACK) {
            openAdmin(admin);
            return;
        }

        int sign = rightClick ? -1 : 1;
        switch (slot) {
            case SLOT_SET_BORDER -> settings.setBorderSize(settings.getBorderSize() + sign * 250);
            case SLOT_SET_FARM -> settings.setFarmMinutes(settings.getFarmMinutes() + sign * 5);
            case SLOT_SET_TARGET -> settings.setTargetSize(settings.getTargetSize() + sign * 100);
            case SLOT_SET_SHRINK -> settings.setShrinkMinutes(settings.getShrinkMinutes() + sign * 5);
            case SLOT_SET_CHUNKS -> settings.setPreloadRadius(settings.getPreloadRadius() + sign * 2);
            case SLOT_SET_TIME -> settings.cycleStartTime(sign);
            case SLOT_SET_COUNTDOWN -> settings.setCountdownSeconds(settings.getCountdownSeconds() + sign);
            case SLOT_SET_LIVES -> settings.setLives(settings.getLives() + sign);
            default -> {
                return;
            }
        }

        openSettings(admin);
    }

    private void handleInviteClick(Player admin, ItemStack clicked) {
        if (!admin.hasPermission("varo.invite")
                || clicked == null || !(clicked.getItemMeta() instanceof SkullMeta skull)) {
            return;
        }

        OfflinePlayer owner = skull.getOwningPlayer();
        Player target = owner == null ? null : owner.getPlayer();
        if (target == null) {
            admin.sendMessage("§cDieser Spieler ist nicht mehr online.");
            return;
        }

        game.invite(target.getUniqueId());
        admin.sendMessage("§a" + target.getName() + " wurde eingeladen.");
        target.sendMessage(invitation(admin));
        target.sendMessage("§7Oder tippe §f/varoaccept §7bzw. §f/varocancel§7.");

        // Liste neu aufbauen, damit die Einladung sofort sichtbar ist.
        openInvite(admin);
    }

    // ------------------------------------------------------------------ Helfer

    /** Einladung mit klickbaren [Annehmen]/[Ablehnen]-Buttons, die die Befehle auslösen. */
    private Component invitation(Player inviter) {
        return Component.text()
                .append(LEGACY.deserialize("§e§lVARO §7» §fDu wurdest von §e" + inviter.getName() + "§f eingeladen! "))
                .append(Component.text("[Annehmen]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/varoaccept"))
                        .hoverEvent(Component.text("Beitreten und in die Lobby", NamedTextColor.GRAY)))
                .append(Component.text(" "))
                .append(Component.text("[Ablehnen]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/varocancel"))
                        .hoverEvent(Component.text("Einladung ablehnen", NamedTextColor.GRAY)))
                .build();
    }

    private void sendToLobbyWorld(Player player) {
        ArenaState arena = game.getArena();
        if (arena == null || !player.getWorld().getName().equals(arena.worldName)) {
            return;
        }

        World lobby = Bukkit.getWorld(plugin.getConfig().getString("lobby-world", "spawn"));
        if (lobby == null) {
            lobby = Bukkit.getWorlds().get(0);
        }
        player.teleport(lobby.getSpawnLocation());
    }

    /** Admins dürfen immer beitreten, alle anderen brauchen eine Einladung. */
    private boolean mayJoin(Player player) {
        return game.isInvited(player.getUniqueId()) || player.hasPermission("varo.admin");
    }

    private String phaseName() {
        return switch (game.getPhase()) {
            case IDLE -> "Keine Arena";
            case LOBBY -> "Lobby";
            case FARM -> "Farmzeit";
            case SHRINK -> "Kampf";
            case ENDED -> "Beendet";
        };
    }

    private Inventory createMenu(VaroMenuHolder.MenuType type, int size, String title) {
        VaroMenuHolder holder = new VaroMenuHolder(type);
        Inventory menu = Bukkit.createInventory(holder, size, text(title));
        holder.setInventory(menu);
        return menu;
    }

    /** Nur Zuschauer in der aktiven Arena dürfen sich zu Spielern teleportieren. */
    private boolean maySpectate(Player player) {
        return player.getGameMode() == GameMode.SPECTATOR && game.isInActiveArena(player);
    }

    private ItemStack head(OfflinePlayer target, String... lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(text("§f" + (target.getName() == null ? "?" : target.getName())));

        List<Component> lines = new ArrayList<>(lore.length);
        for (String line : lore) {
            lines.add(text(line));
        }
        meta.lore(lines);

        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(text(name));

        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>(lore.length);
            for (String line : lore) {
                lines.add(text(line));
            }
            meta.lore(lines);
        }

        stack.setItemMeta(meta);
        return stack;
    }

    /** Wandelt einen §-Text in eine Component ohne das Standard-Kursiv um. */
    private Component text(String legacy) {
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }
}
