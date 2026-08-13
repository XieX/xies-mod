package mod.xie.xm.content.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockIdsTest {

    @Test
    void resolvesTheIdsSaltDeclares() {
        // salt.xie says "spawnsNear": "8,9" and "spawnsUnder": 0, which is the whole
        // reason this table exists.
        assertEquals(Optional.of("water"), LegacyBlockIds.path("8"));
        assertEquals(Optional.of("water"), LegacyBlockIds.path("9"));
        assertEquals(Optional.of("air"), LegacyBlockIds.path("0"));
    }

    @Test
    void mapsBothStatesOfASplitBlockOntoTheOneModernBlock() {
        assertEquals(LegacyBlockIds.path(10), LegacyBlockIds.path(11), "lava");
        assertEquals(LegacyBlockIds.path(61), LegacyBlockIds.path(62), "furnace");
        assertEquals(LegacyBlockIds.path(73), LegacyBlockIds.path(74), "redstone ore");
    }

    @Test
    void knowsTheBlocksThatWereRenamed() {
        assertEquals(Optional.of("grass_block"), LegacyBlockIds.path(2));
        assertEquals(Optional.of("short_grass"), LegacyBlockIds.path(31));
        assertEquals(Optional.of("oak_planks"), LegacyBlockIds.path(5));
        assertEquals(Optional.of("poppy"), LegacyBlockIds.path(38));
    }

    @Test
    void hasNothingForIdsThatWereNeverBlocks() {
        // 95 was the locked chest, which never shipped, and 256 up were items.
        assertTrue(LegacyBlockIds.path(95).isEmpty());
        assertTrue(LegacyBlockIds.path(260).isEmpty());
        assertTrue(LegacyBlockIds.path(-1).isEmpty());
    }

    @Test
    void leavesNamesAlone() {
        assertTrue(LegacyBlockIds.path("sand").isEmpty());
        assertTrue(LegacyBlockIds.path("").isEmpty());
        assertFalse(LegacyBlockIds.isNumeric("tallgrass"));
        assertFalse(LegacyBlockIds.isNumeric("8a"));
        assertTrue(LegacyBlockIds.isNumeric("8"));
    }
}
