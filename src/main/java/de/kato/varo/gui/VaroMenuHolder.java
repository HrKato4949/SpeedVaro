package de.kato.varo.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker für unsere eigenen Menüs. Über den Holder erkennt der Klick-Listener
 * sicher, ob ein Inventar zu diesem Plugin gehört - zuverlässiger als ein
 * Vergleich des Menü-Titels.
 */
public class VaroMenuHolder implements InventoryHolder {

    public enum MenuType {
        MAIN, INVITE, TEAMS, ADMIN, SETTINGS, SPECTATE, KICK
    }

    private final MenuType type;
    private Inventory inventory;

    public VaroMenuHolder(MenuType type) {
        this.type = type;
    }

    public MenuType getType() {
        return type;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
