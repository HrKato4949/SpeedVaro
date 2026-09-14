package de.kato.varo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hält den laufenden Spielzustand im Speicher: aktuelle Phase, Teilnehmer,
 * offene Einladungen und den Timer der aktuellen Phase. Bewusst nicht
 * persistent - ein Serverneustart mitten in der Runde würde das Spiel
 * ohnehin zerreißen. Die Arena-Koordinaten liegen dafür in {@link ArenaState},
 * das hier zusätzlich zwischengespeichert wird, damit Listener und Scoreboard
 * nicht sekündlich die arena.yml von der Platte lesen müssen.
 */
public class VaroGame {

    public enum Phase {
        /** Noch keine Arena aufgebaut - /varosetup fehlt. */
        IDLE,
        /** Käfig steht, Spieler können eingeladen werden und beitreten. */
        LOBBY,
        /** Drop ist gelaufen, Farmzeit läuft. */
        FARM,
        /** Border schrumpft. */
        SHRINK,
        /** Runde vorbei - Sieger steht fest. */
        ENDED
    }

    private Phase phase = Phase.IDLE;
    private ArenaState arena;

    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> invited = new HashSet<>();
    /** Teilnehmer, die noch nicht ausgeschieden sind. */
    private final Set<UUID> alive = new LinkedHashSet<>();
    /** Verbleibende Leben pro Teilnehmer; bei 0 ist man raus. */
    private final Map<UUID, Integer> lives = new HashMap<>();
    private int maxLives;
    private final List<VaroTeam> teams = new ArrayList<>();
    private String winnerName;

    /** Zeitpunkt, an dem die aktuelle Phase endet (0 = kein Timer). */
    private long phaseEndMillis;
    private double targetSize;

    public Phase getPhase() {
        return phase;
    }

    public ArenaState getArena() {
        return arena;
    }

    /** Steht der Spieler in der Arena-Welt, während dort eine Runde vorbereitet wird oder läuft? */
    public boolean isInActiveArena(Player player) {
        return arena != null
                && phase != Phase.IDLE
                && player.getWorld().getName().equals(arena.worldName);
    }

    public void setArena(ArenaState arena) {
        this.arena = arena;
    }

    /** Alles auf Anfang - kein Spiel, keine Teilnehmer. */
    public void reset() {
        participants.clear();
        invited.clear();
        alive.clear();
        lives.clear();
        // Sonst könnte jemand nach dem Reset noch Items aus dem alten
        // Backpack ziehen.
        for (VaroTeam team : teams) {
            team.closeBackpack();
        }
        teams.clear();
        winnerName = null;
        phaseEndMillis = 0;
        phase = Phase.IDLE;
    }

    public List<VaroTeam> getTeams() {
        return teams;
    }

    /** Legt das nächste Team an; null, wenn die Farbpalette ausgeschöpft ist. */
    public VaroTeam createTeam() {
        if (teams.size() >= VaroTeam.MAX_TEAMS) {
            return null;
        }

        VaroTeam team = new VaroTeam(teams.size());
        teams.add(team);
        return team;
    }

    public VaroTeam getTeam(UUID uuid) {
        for (VaroTeam team : teams) {
            if (team.getMembers().contains(uuid)) {
                return team;
            }
        }
        return null;
    }

    /** Wechselt das Team; ein Spieler ist immer in höchstens einem Team. */
    public void joinTeam(UUID uuid, VaroTeam team) {
        leaveTeam(uuid);
        team.getMembers().add(uuid);
    }

    public void leaveTeam(UUID uuid) {
        for (VaroTeam team : teams) {
            team.getMembers().remove(uuid);
        }
    }

    /** Neue Runde: Teilnehmer und Einladungen zurücksetzen, Lobby öffnen. */
    public void openLobby() {
        reset();
        phase = Phase.LOBBY;
    }

    public void startFarm(int farmMinutes, double targetSize, int livesPerPlayer) {
        this.phase = Phase.FARM;
        this.phaseEndMillis = System.currentTimeMillis() + farmMinutes * 60_000L;
        this.targetSize = targetSize;
        this.maxLives = livesPerPlayer;
        invited.clear();

        // Ab jetzt zählt, wer noch lebt.
        alive.clear();
        alive.addAll(participants);
        lives.clear();
        for (UUID uuid : participants) {
            lives.put(uuid, livesPerPlayer);
        }
    }

    public int getMaxLives() {
        return maxLives;
    }

    public int getLives(UUID uuid) {
        return lives.getOrDefault(uuid, 0);
    }

    /** Zieht ein Leben ab und gibt die verbleibenden zurück. */
    public int loseLife(UUID uuid) {
        int left = Math.max(getLives(uuid) - 1, 0);
        lives.put(uuid, left);
        return left;
    }

    /** Beendet die Runde; winnerName darf null sein (niemand übrig). */
    public void end(String winnerName) {
        this.phase = Phase.ENDED;
        this.phaseEndMillis = 0;
        this.winnerName = winnerName;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public void startShrink(int shrinkSeconds) {
        this.phase = Phase.SHRINK;
        this.phaseEndMillis = System.currentTimeMillis() + shrinkSeconds * 1000L;
    }

    /** Verbleibende Sekunden der aktuellen Phase, nie negativ. */
    public long getRemainingSeconds() {
        if (phaseEndMillis == 0) {
            return 0;
        }
        return Math.max((phaseEndMillis - System.currentTimeMillis()) / 1000L, 0);
    }

    public double getTargetSize() {
        return targetSize;
    }

    public void invite(UUID uuid) {
        invited.add(uuid);
    }

    public void uninvite(UUID uuid) {
        invited.remove(uuid);
    }

    public boolean isInvited(UUID uuid) {
        return invited.contains(uuid);
    }

    /** Mitte des Glaskäfigs, oder null, wenn gerade kein Käfig steht. */
    public Location getCageLocation() {
        return arena != null && arena.cageActive ? getSkyLocation() : null;
    }

    /** Der Punkt hoch über dem Arena-Zentrum - Käfigposition, auch wenn der Käfig weg ist. */
    public Location getSkyLocation() {
        if (arena == null) {
            return null;
        }

        World world = Bukkit.getWorld(arena.worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, arena.centerX + 0.5, arena.cageY + 1, arena.centerZ + 0.5);
    }

    public void addParticipant(UUID uuid) {
        participants.add(uuid);
        invited.remove(uuid);
    }

    public void removeParticipant(UUID uuid) {
        participants.remove(uuid);
        alive.remove(uuid);
        leaveTeam(uuid);
    }

    public void eliminate(UUID uuid) {
        alive.remove(uuid);
    }

    public boolean isAlive(UUID uuid) {
        return alive.contains(uuid);
    }

    public Set<UUID> getAlive() {
        return alive;
    }

    public boolean isParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    public Set<UUID> getParticipants() {
        return participants;
    }
}
