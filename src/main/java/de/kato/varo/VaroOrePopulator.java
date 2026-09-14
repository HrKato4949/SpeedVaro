package de.kato.varo;

import org.bukkit.Material;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Mehr Diamanten in der Varo-Welt: Bei jedem neu erzeugten Chunk werden
 * zusätzliche kleine Diamantadern in Stein und Tiefenschiefer gesetzt.
 *
 * Läuft mit der Weltgenerierung (auch auf deren Threads), fasst deshalb nur
 * die übergebene Region an. Wirkt nur auf neue Chunks - da jede Arena an
 * unberührten Koordinaten liegt, ist das genau das Spielfeld.
 */
public class VaroOrePopulator extends BlockPopulator {

    /** Vanilla-Diamantbereich. */
    private static final int MIN_Y = -58;
    private static final int MAX_Y = 14;
    private static final int MAX_VEIN_SIZE = 4;

    private final int veinsPerChunk;

    public VaroOrePopulator(int veinsPerChunk) {
        this.veinsPerChunk = veinsPerChunk;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        for (int vein = 0; vein < veinsPerChunk; vein++) {
            int x = (chunkX << 4) + random.nextInt(16);
            int z = (chunkZ << 4) + random.nextInt(16);
            int y = MIN_Y + random.nextInt(MAX_Y - MIN_Y);
            int size = 1 + random.nextInt(MAX_VEIN_SIZE);

            // Kleine Klumpen wie in Vanilla: bis zu 2x2x2 um den Startpunkt.
            for (int block = 0; block < size; block++) {
                place(region,
                        x + random.nextInt(2),
                        y + random.nextInt(2),
                        z + random.nextInt(2));
            }
        }
    }

    private void place(LimitedRegion region, int x, int y, int z) {
        if (!region.isInRegion(x, y, z)) {
            return;
        }

        Material current = region.getType(x, y, z);
        if (current == Material.STONE) {
            region.setType(x, y, z, Material.DIAMOND_ORE);
        } else if (current == Material.DEEPSLATE) {
            region.setType(x, y, z, Material.DEEPSLATE_DIAMOND_ORE);
        }
    }
}
