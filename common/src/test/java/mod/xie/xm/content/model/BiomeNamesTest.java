package mod.xie.xm.content.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiomeNamesTest {

    @Test
    void mapsTheBiomesTheLiveContentNames() {
        assertEquals(List.of("#c:is_plains"), BiomeNames.modern("plains"));
        assertEquals(List.of("#c:is_forest"), BiomeNames.modern("forest"));
        assertEquals(List.of("#c:is_swamp"), BiomeNames.modern("swamp"));
        assertEquals(List.of("#c:is_desert"), BiomeNames.modern("desert"));
        assertEquals(List.of("#c:is_hill"), BiomeNames.modern("hills"));
        assertEquals(List.of("#c:is_savanna"), BiomeNames.modern("savanna"));
    }

    @Test
    void usesConcreteIdsWhereNoTagFits() {
        assertEquals(List.of("minecraft:meadow"), BiomeNames.modern("meadow"));
    }

    @Test
    void hasNothingForBiomesVanillaNeverHad() {
        // Both came from the biome mods of the day; nothing modern tags anything as
        // either, so they resolve to nothing rather than to something approximate.
        assertTrue(BiomeNames.modern("marsh").isEmpty());
        assertTrue(BiomeNames.modern("shrubland").isEmpty());
        assertTrue(BiomeNames.modern("").isEmpty());
        assertTrue(BiomeNames.modern(null).isEmpty());
    }

    @Test
    void ignoresCaseAndSpacing() {
        assertEquals(BiomeNames.modern("hills"), BiomeNames.modern("Extreme Hills"),
            "\"Extreme Hills\" should reduce to the same tag as \"extremehills\"");
        assertEquals(BiomeNames.modern("taiga"), BiomeNames.modern("  TAIGA "));
    }
}
