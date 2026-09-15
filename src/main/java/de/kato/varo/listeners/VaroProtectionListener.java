package de.kato.varo.listeners;

import de.kato.varo.ArenaState;
import de.kato.varo.Lang;
import de.kato.varo.VaroGame;
import de.kato.varo.VaroTeam;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Phasenabhängiger Schutz in der Varo-Welt:
 * - Lobby: nichts abbauen/setzen (außer Kreativ), gar kein Schaden, kein Hunger
 * - Farmzeit: gar kein Schaden (auch nicht Lava, Feuer, Mobs), kein Hunger
 * - Kampf: alles frei
 * - Beendet: wieder gar kein Schaden
 * - Teamkollegen verletzen sich nie, stoßen sich aber ganz normal weg
 * - Netherportale gehen in der Arena-Welt nie
 * - Feindliche Mobs spawnen in der Arena-Welt nicht (abschaltbar)
 */
public class VaroProtectionListener implements Listener {

    private final JavaPlugin plugin;
    private final VaroGame game;

    public VaroProtectionListener(JavaPlugin plugin, VaroGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isBuildProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isBuildProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !game.isInActiveArena(victim)) {
            return;
        }

        // /kill und Void gehen immer durch - sonst kann ein Admin in der
        // Farmzeit nichts testen und wer unter die Welt fällt, hängt fest.
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.KILL || cause == EntityDamageEvent.DamageCause.VOID) {
            return;
        }

        VaroGame.Phase phase = game.getPhase();
        Player attacker = event instanceof EntityDamageByEntityEvent byEntity
                ? attackingPlayer(byEntity.getDamager())
                : null;

        // Teamkollegen: Schaden auf 0, Event aber NICHT abbrechen - so bleibt
        // der Vanilla-Rückstoß erhalten und man kann sich gegenseitig
        // "boosten", ohne sich zu verletzen.
        if (attacker != null && phase != VaroGame.Phase.LOBBY && isSameTeam(attacker, victim)) {
            event.setDamage(0);
            return;
        }

        // Nach dem Sieg ist auch Schluss - sonst würde das Feuerwerk verletzen
        // oder jemand die Zuschauer-Phase zum Nachtreten nutzen.
        if (phase == VaroGame.Phase.LOBBY || phase == VaroGame.Phase.FARM || phase == VaroGame.Phase.ENDED) {
            event.setCancelled(true);
            // Sonst brennt man sichtbar weiter, obwohl nichts passiert.
            victim.setFireTicks(0);
        }
    }

    /**
     * Keine Zombies, Creeper, Phantome usw. in der Arena - das ersetzt die
     * Gamerule, die man sonst pro Welt setzen müsste. Spawn-Eier, /summon und
     * andere Plugins dürfen weiterhin; Tiere sind nie betroffen.
     */
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Enemy) || !isArenaWorld(event.getEntity().getWorld())
                || !plugin.getConfig().getBoolean("block-hostile-mobs", true)) {
            return;
        }

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                && reason != CreatureSpawnEvent.SpawnReason.COMMAND
                && reason != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    /** Kein Nether in Varo: Portale lassen sich in der Arena-Welt nicht entzünden ... */
    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        if (!isArenaWorld(event.getWorld())) {
            return;
        }

        event.setCancelled(true);
        if (event.getEntity() instanceof Player player) {
            player.sendMessage(Lang.get("protection.nether-blocked"));
        }
    }

    /** ... und ein bereits vorhandenes Portal führt nirgendwohin. */
    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (game.isInActiveArena(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Lang.get("protection.nether-blocked"));
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !game.isInActiveArena(player)) {
            return;
        }

        VaroGame.Phase phase = game.getPhase();
        if (phase != VaroGame.Phase.LOBBY && phase != VaroGame.Phase.FARM) {
            return;
        }

        // Nur das Absinken abfangen - Essen darf den Balken weiterhin füllen.
        if (event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    /** Der Spieler hinter einem Schlag oder Projektil (Pfeil, Trident, ...), sonst null. */
    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private boolean isSameTeam(Player a, Player b) {
        VaroTeam team = game.getTeam(a.getUniqueId());
        return team != null && team == game.getTeam(b.getUniqueId());
    }

    /** Die Arena-Welt, solange dort eine Runde vorbereitet wird oder läuft. */
    private boolean isArenaWorld(World world) {
        ArenaState arena = game.getArena();
        return arena != null
                && game.getPhase() != VaroGame.Phase.IDLE
                && world.getName().equals(arena.worldName);
    }

    private boolean isBuildProtected(Player player) {
        return game.isInActiveArena(player)
                && game.getPhase() == VaroGame.Phase.LOBBY
                && player.getGameMode() != GameMode.CREATIVE;
    }
}
