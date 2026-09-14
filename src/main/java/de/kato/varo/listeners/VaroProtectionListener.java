package de.kato.varo.listeners;

import de.kato.varo.ArenaState;
import de.kato.varo.VaroGame;
import de.kato.varo.VaroTeam;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;

/**
 * Phasenabhängiger Schutz in der Varo-Welt:
 * - Lobby: nichts abbauen/setzen (außer Kreativ), gar kein Schaden, kein Hunger
 * - Farmzeit: gar kein Schaden (auch nicht Lava, Feuer, Mobs), kein Hunger
 * - Kampf: alles frei
 * - Beendet: wieder gar kein Schaden
 * - Teamkollegen verletzen sich nie, stoßen sich aber ganz normal weg
 * - Netherportale gehen in der Arena-Welt nie
 */
public class VaroProtectionListener implements Listener {

    private final VaroGame game;

    public VaroProtectionListener(VaroGame game) {
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

    /** Kein Nether in Varo: Portale lassen sich in der Arena-Welt nicht entzünden ... */
    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        ArenaState arena = game.getArena();
        if (arena == null || game.getPhase() == VaroGame.Phase.IDLE
                || !event.getWorld().getName().equals(arena.worldName)) {
            return;
        }

        event.setCancelled(true);
        if (event.getEntity() instanceof Player player) {
            player.sendMessage("§cDer Nether ist in Varo gesperrt.");
        }
    }

    /** ... und ein bereits vorhandenes Portal führt nirgendwohin. */
    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (game.isInActiveArena(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cDer Nether ist in Varo gesperrt.");
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

    private boolean isBuildProtected(Player player) {
        return game.isInActiveArena(player)
                && game.getPhase() == VaroGame.Phase.LOBBY
                && player.getGameMode() != GameMode.CREATIVE;
    }
}
