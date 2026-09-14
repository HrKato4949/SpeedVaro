package de.kato.varo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Steuert den Fortnite-artigen Sturzflug beim Drop.
 *
 * Wichtig: Minecraft prüft jeden Tick, ob im Brustslot tatsächlich eine Elytra
 * steckt, und setzt das Gleit-Flag sonst sofort wieder zurück - ein reines
 * setGliding(true) reicht auf aktuellen Versionen also nicht mehr. Deshalb
 * bekommt jeder Spieler für den Flug eine unzerstörbare Elytra angezogen, die
 * beim Landen automatisch wieder verschwindet; das vorherige Brustteil wird
 * zwischengespeichert und danach zurückgegeben.
 */
public class VaroGlideManager implements Listener {

    /** Erst danach auf Bodenkontakt prüfen - sonst endet der Flug sofort. */
    private static final long MIN_GLIDE_MILLIS = 2000;
    /** Sicherheitsnetz, falls jemand nie sauber "landet". */
    private static final long MAX_GLIDE_MILLIS = 120_000;

    private final JavaPlugin plugin;
    private final NamespacedKey glideKey;
    private final Map<UUID, Glide> gliding = new HashMap<>();

    private record Glide(ItemStack previousChestplate, long startMillis) { }

    public VaroGlideManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.glideKey = new NamespacedKey(plugin, "glide_elytra");
    }

    /** Prüft regelmäßig, wer gelandet ist. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkLanded, 20L, 5L);
    }

    /** Zieht dem Spieler die Flug-Elytra an und startet den Gleitflug. */
    public void startGlide(Player player) {
        gliding.put(player.getUniqueId(),
                new Glide(player.getInventory().getChestplate(), System.currentTimeMillis()));

        player.getInventory().setChestplate(createElytra());

        // Ein paar Ticks warten: Gleiten lässt sich nur auslösen, wenn der
        // Spieler bereits fällt - direkt nach dem Entfernen des Käfigbodens
        // steht er dafür noch zu fest auf dem Block.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.setGliding(true);
            }
        }, 3L);
    }

    /** Beendet einen laufenden Flug sofort und gibt das Brustteil zurück. */
    public void endGlide(Player player) {
        Glide glide = gliding.remove(player.getUniqueId());
        if (glide != null) {
            restore(player, glide);
        }
    }

    /** Gibt allen noch fliegenden Spielern ihr Brustteil zurück. */
    public void stop() {
        for (Map.Entry<UUID, Glide> entry : gliding.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                restore(player, entry.getValue());
            }
        }
        gliding.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Glide glide = gliding.remove(event.getPlayer().getUniqueId());
        if (glide != null) {
            restore(event.getPlayer(), glide);
        }
    }

    // Die Flug-Elytra soll niemand behalten können: Sie lässt sich weder aus
    // dem Rüstungsslot ziehen noch wegwerfen, und beim Tod fällt sie nicht.

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (isGlideElytra(event.getCurrentItem()) || isGlideElytra(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isGlideElytra(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        if (isGlideElytra(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isGlideElytra(event.getMainHandItem()) || isGlideElytra(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * HIGH, damit der VaroDeathListener vorher entschieden hat, ob das
     * Inventar behalten wird - davon hängt ab, wohin die Brustplatte muss.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        event.getDrops().removeIf(this::isGlideElytra);

        Glide glide = gliding.remove(player.getUniqueId());
        if (glide == null || glide.previousChestplate() == null) {
            return;
        }

        // Stirbt man mitten im Flug, darf die gemerkte Brustplatte nicht
        // verloren gehen: zurück ins behaltene Inventar oder in die Drops.
        if (event.getKeepInventory()) {
            player.getInventory().setChestplate(glide.previousChestplate());
        } else {
            event.getDrops().add(glide.previousChestplate());
        }
    }

    /**
     * Solange der Flug von uns gesteuert wird, kostet eine harte Landung
     * nichts - wichtig für den Respawn-Drop in der Kampfphase, wo sonst
     * Fallschaden gilt.
     */
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !gliding.containsKey(player.getUniqueId())) {
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FALL || cause == EntityDamageEvent.DamageCause.FLY_INTO_WALL) {
            event.setCancelled(true);
        }
    }

    /** Erkennt unsere Flug-Elytra an ihrer Markierung, nicht am Aussehen. */
    private boolean isGlideElytra(ItemStack stack) {
        if (stack == null || stack.getType() != Material.ELYTRA) {
            return false;
        }

        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(glideKey, PersistentDataType.BYTE);
    }

    private void checkLanded() {
        Iterator<Map.Entry<UUID, Glide>> iterator = gliding.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Glide> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());

            if (player == null) {
                iterator.remove();
                continue;
            }

            long flownMillis = System.currentTimeMillis() - entry.getValue().startMillis();
            if (flownMillis < MIN_GLIDE_MILLIS) {
                continue;
            }

            if (player.isOnGround() || !player.isGliding() || flownMillis > MAX_GLIDE_MILLIS) {
                restore(player, entry.getValue());
                iterator.remove();
            }
        }
    }

    private void restore(Player player, Glide glide) {
        player.setGliding(false);

        ItemStack previous = glide.previousChestplate();
        if (isGlideElytra(player.getInventory().getChestplate())) {
            // Normalfall: Flug-Elytra raus, altes Teil wieder rein (null = leer).
            player.getInventory().setChestplate(previous);
        } else if (previous != null) {
            // Der Spieler hat unterwegs etwas anderes angezogen - das alte Teil
            // dann nicht überschreiben, sondern ins Inventar legen.
            for (ItemStack rest : player.getInventory().addItem(previous).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }

        // Sicherheitsnetz, falls die Flug-Elytra doch irgendwo im Inventar liegt.
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isGlideElytra(contents[slot])) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private ItemStack createElytra() {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        // Ohne das würde der Flug an der Haltbarkeit scheitern können.
        meta.setUnbreakable(true);
        // Markierung, an der wir sie später sicher wiedererkennen.
        meta.getPersistentDataContainer().set(glideKey, PersistentDataType.BYTE, (byte) 1);
        elytra.setItemMeta(meta);
        return elytra;
    }
}
