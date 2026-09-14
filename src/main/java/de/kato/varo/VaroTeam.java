package de.kato.varo;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Ein Varo-Team. Farbe, Anzeigename und Wolle fürs Menü ergeben sich aus dem
 * Index, damit jedes Team automatisch eine eigene, klar unterscheidbare Farbe
 * bekommt.
 */
public class VaroTeam {

    /** Mehr Teams als Farben in der Palette wären nicht mehr unterscheidbar. */
    public static final int MAX_TEAMS = 8;

    private static final NamedTextColor[] COLORS = {
            NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.YELLOW,
            NamedTextColor.AQUA, NamedTextColor.LIGHT_PURPLE, NamedTextColor.GOLD, NamedTextColor.DARK_GREEN
    };

    private static final String[] LEGACY_COLORS = {
            "§c", "§9", "§a", "§e", "§b", "§d", "§6", "§2"
    };

    private static final Material[] WOOL = {
            Material.RED_WOOL, Material.BLUE_WOOL, Material.LIME_WOOL, Material.YELLOW_WOOL,
            Material.LIGHT_BLUE_WOOL, Material.MAGENTA_WOOL, Material.ORANGE_WOOL, Material.GREEN_WOOL
    };

    private static final int BACKPACK_SIZE = 27;

    private final int index;
    private final Set<UUID> members = new LinkedHashSet<>();
    private Inventory backpack;

    public VaroTeam(int index) {
        this.index = index;
    }

    /**
     * Gemeinsame Kiste des Teams. Ein einziges Inventar-Objekt für alle
     * Mitglieder, damit Änderungen sofort bei jedem sichtbar sind, der es
     * gerade offen hat. Lebt nur für die Runde - wie das Team selbst.
     */
    public Inventory getBackpack() {
        if (backpack == null) {
            backpack = Bukkit.createInventory(null, BACKPACK_SIZE,
                    LegacyComponentSerializer.legacySection()
                            .deserialize(getLegacyColor() + "§lBackpack §8» " + getLegacyColor() + getName()));
        }
        return backpack;
    }

    /** Schließt den Backpack bei allen, die ihn offen haben (z.B. beim Reset). */
    public void closeBackpack() {
        if (backpack == null) {
            return;
        }
        for (HumanEntity viewer : new ArrayList<>(backpack.getViewers())) {
            viewer.closeInventory();
        }
    }

    public int getIndex() {
        return index;
    }

    public String getName() {
        return "Team " + (index + 1);
    }

    public NamedTextColor getColor() {
        return COLORS[index % COLORS.length];
    }

    /** Farbcode für die Chat-/Scoreboard-Texte. */
    public String getLegacyColor() {
        return LEGACY_COLORS[index % LEGACY_COLORS.length];
    }

    public Material getWool() {
        return WOOL[index % WOOL.length];
    }

    public Set<UUID> getMembers() {
        return members;
    }
}
