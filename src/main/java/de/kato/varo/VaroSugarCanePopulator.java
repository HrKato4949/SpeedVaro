package de.kato.varo;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Mehr Zuckerrohr in der Varo-Welt, aber wie in Vanilla: Es wächst nur an
 * Ufern, auf Sand, Gras oder Erde direkt neben Wasser. Pro neuem Chunk
 * werden ein paar Stellen probiert; wo ein Fluss oder See liegt, entstehen
 * mehrere Büschel. In komplett trockenen Gegenden entsteht ab und zu ein
 * kleiner Tümpel mit Zuckerrohr drumherum, damit Papier nie unerreichbar ist.
 *
 * Läuft mit der Weltgenerierung - nur die übergebene Region anfassen.
 */
public class VaroSugarCanePopulator extends BlockPopulator {

    private static final int SHORE_ATTEMPTS = 8;
    private static final double POND_CHANCE = 0.04;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        boolean placed = false;

        for (int attempt = 0; attempt < SHORE_ATTEMPTS; attempt++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);

            // Nur Wasseroberflächen als Ausgangspunkt - so bleibt es "natürlich".
            if (region.getType(x, y, z) != Material.WATER) {
                continue;
            }

            for (int[] side : SIDES) {
                int nx = x + side[0];
                int nz = z + side[1];
                if (canGrowOn(region, nx, y, nz) && random.nextInt(3) > 0) {
                    plant(region, random, nx, y + 1, nz);
                    placed = true;
                }
            }
        }

        if (!placed && random.nextDouble() < POND_CHANCE) {
            digPond(region, random, baseX + 4 + random.nextInt(8), baseZ + 4 + random.nextInt(8));
        }
    }

    /** Ufer auf Höhe der Wasseroberfläche mit freiem Platz darüber. */
    private boolean canGrowOn(LimitedRegion region, int x, int y, int z) {
        if (!region.isInRegion(x, y + 3, z)) {
            return false;
        }

        Material ground = region.getType(x, y, z);
        boolean soil = ground == Material.SAND || ground == Material.GRASS_BLOCK || ground == Material.DIRT;
        return soil && region.getType(x, y + 1, z).isAir();
    }

    /** Zuckerrohr 2-4 hoch wie in Vanilla. */
    private void plant(LimitedRegion region, Random random, int x, int y, int z) {
        int height = 2 + random.nextInt(3);
        for (int i = 0; i < height; i++) {
            if (region.isInRegion(x, y + i, z) && region.getType(x, y + i, z).isAir()) {
                region.setType(x, y + i, z, Material.SUGAR_CANE);
            }
        }
    }

    /** Kleiner Tümpel: ein Wasserblock im Gras, Zuckerrohr an drei Seiten. */
    private void digPond(LimitedRegion region, Random random, int x, int z) {
        int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (!region.isInRegion(x, y + 3, z) || region.getType(x, y, z) != Material.GRASS_BLOCK) {
            return;
        }

        // Nur auf ebenem Gras, sonst hängt der Tümpel am Hang in der Luft.
        for (int[] side : SIDES) {
            if (region.getType(x + side[0], y, z + side[1]) != Material.GRASS_BLOCK) {
                return;
            }
        }

        region.setType(x, y, z, Material.WATER);
        int skip = random.nextInt(SIDES.length);
        for (int i = 0; i < SIDES.length; i++) {
            if (i != skip) {
                plant(region, random, x + SIDES[i][0], y + 1, z + SIDES[i][1]);
            }
        }
    }
}
