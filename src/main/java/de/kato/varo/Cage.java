package de.kato.varo;

import org.bukkit.Material;
import org.bukkit.World;

/**
 * Baut und entfernt den Glaskäfig der Lobby. Geteilt zwischen /varosetup,
 * /varostart und /varoreset, damit die Maße nur an einer Stelle stehen.
 */
public final class Cage {

    private Cage() {
    }

    /** Hohle Glashülle mit Boden und Decke. */
    public static void build(World world, ArenaState arena) {
        forEachBlock(world, arena, true);
    }

    /** Löst den Käfig wieder in Luft auf. */
    public static void clear(World world, ArenaState arena) {
        forEachBlock(world, arena, false);
    }

    private static void forEachBlock(World world, ArenaState arena, boolean build) {
        for (int x = -arena.cageRadius; x <= arena.cageRadius; x++) {
            for (int z = -arena.cageRadius; z <= arena.cageRadius; z++) {
                for (int y = 0; y <= arena.cageHeight; y++) {

                    boolean shell = y == 0 || y == arena.cageHeight
                            || x == -arena.cageRadius || x == arena.cageRadius
                            || z == -arena.cageRadius || z == arena.cageRadius;

                    world.getBlockAt(arena.centerX + x, arena.cageY + y, arena.centerZ + z)
                            .setType(build && shell ? Material.GLASS : Material.AIR);
                }
            }
        }
    }
}
