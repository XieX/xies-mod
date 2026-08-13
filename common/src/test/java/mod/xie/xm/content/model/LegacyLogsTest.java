package mod.xie.xm.content.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyLogsTest {

    @Test
    void mapsTheFourWoodTypesOntoTheirOwnBlocks() {
        assertEquals(Optional.of("oak_log"), LegacyLogs.path(0));
        assertEquals(Optional.of("spruce_log"), LegacyLogs.path(1));
        assertEquals(Optional.of("birch_log"), LegacyLogs.path(2));
        assertEquals(Optional.of("jungle_log"), LegacyLogs.path(3));
    }

    /**
     * 1.6 had four woods on one id, so the upper metadata values were the log's
     * orientation rather than another wood. {@code getTrunkMeta} answered 0 for
     * anything past the declared array, and oak is what 0 meant.
     */
    @Test
    void anythingPastTheFourthWoodIsOak() {
        assertEquals(Optional.empty(), LegacyLogs.path(4));
        assertEquals("oak_log", LegacyLogs.pathOrOak(4));
        assertEquals("oak_log", LegacyLogs.pathOrOak(-1));
    }

    /** {@code cocoaLeaves} writes {@code "trunk": "log"}; a spawn entry might say 17. */
    @Test
    void recognisesTheLogBlockByNameAndById() {
        assertTrue(LegacyLogs.isLegacyLog("log"));
        assertTrue(LegacyLogs.isLegacyLog(" LOG "));
        assertTrue(LegacyLogs.isLegacyLog("17"));
    }

    /**
     * {@code Block.planks} was the one whose unlocalized name was "wood"; the log's
     * was "log". Treating "wood" as the log would give a trunk of planks a wood type
     * it never had.
     */
    @Test
    void woodIsThePlanks() {
        assertFalse(LegacyLogs.isLegacyLog("wood"));
        assertFalse(LegacyLogs.isLegacyLog("oak_log"));
        assertFalse(LegacyLogs.isLegacyLog(null));
    }
}
