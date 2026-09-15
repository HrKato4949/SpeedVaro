package de.kato.varo;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;


/**
 * Der "Höhlen-Aufzug": ein einmaliges Item, das jeder beim Drop bekommt.
 * Rechtsklick unterhalb von Y=0 teleportiert an die Oberfläche direkt
 * darüber. Erkannt wird es an einer Markierung im PersistentDataContainer,
 * nicht am Aussehen - ein umbenannter Echo-Splitter funktioniert also nicht.
 */
public class VaroElevator implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int MAX_USE_Y = 0;

    private final VaroGame game;
    private final NamespacedKey key;

    public VaroElevator(JavaPlugin plugin, VaroGame game) {
        this.game = game;
        this.key = new NamespacedKey(plugin, "elevator");
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(Lang.get("elevator.name")).decoration(TextDecoration.ITALIC, false));
        meta.lore(Lang.list("elevator.lore").stream()
                .<Component>map(line -> LEGACY.deserialize(line).decoration(TextDecoration.ITALIC, false))
                .toList());
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!isElevator(item)) {
            return;
        }

        // Kein Blockplatzieren o.ä. mit dem Item - egal ob es jetzt zündet.
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!game.isInActiveArena(player)) {
            return;
        }

        Location from = player.getLocation();
        if (from.getBlockY() >= MAX_USE_Y) {
            player.sendMessage(Lang.get("elevator.too-high"));
            return;
        }

        int surfaceY = from.getWorld().getHighestBlockYAt(from.getBlockX(), from.getBlockZ()) + 1;
        Location target = new Location(from.getWorld(),
                from.getBlockX() + 0.5, surfaceY, from.getBlockZ() + 0.5,
                from.getYaw(), from.getPitch());

        // Einmalig: ein Stück verbrauchen.
        item.setAmount(item.getAmount() - 1);

        player.setFallDistance(0f);
        player.teleport(target);
        player.playSound(Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.PLAYER, 1f, 1.2f));
        player.sendMessage(Lang.get("elevator.used"));
    }

    private boolean isElevator(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
