package de.kato.varo.commands;

import de.kato.varo.Lang;
import de.kato.varo.VaroGame;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /varoaccept - nimmt eine Einladung an: Spieler wird Teilnehmer und landet
 * im Glaskäfig. Wird auch vom [Annehmen]-Button im Chat und vom
 * Beitreten-Button im /varo-Menü genutzt.
 */
public class VaroAcceptCommand implements CommandExecutor {

    private final VaroGame game;

    public VaroAcceptCommand(VaroGame game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("general.players-only"));
            return true;
        }

        accept(player);
        return true;
    }

    public void accept(Player player) {
        UUID uuid = player.getUniqueId();

        if (game.getPhase() != VaroGame.Phase.LOBBY) {
            player.sendMessage(Lang.get("join.no-lobby"));
            return;
        }

        if (game.isParticipant(uuid)) {
            player.sendMessage(Lang.get("join.already"));
            return;
        }

        // Admins dürfen ohne Einladung rein - praktisch zum Testen.
        if (!game.isInvited(uuid) && !player.hasPermission("varo.admin")) {
            player.sendMessage(Lang.get("join.not-invited"));
            return;
        }

        game.addParticipant(uuid);

        Location cage = game.getCageLocation();
        if (cage != null) {
            player.teleport(cage);
        }

        player.sendMessage(Lang.get("join.joined"));
        player.sendMessage(Lang.get("join.leave-hint"));
    }
}
