package mod.xie.xm.content.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlantTypesTest {

    /** The one the content declares, and the whole point of a nether crop. */
    @Test
    void netherIsSoulSand() {
        assertEquals(List.of("minecraft:soul_sand"), PlantTypes.soilsFor("nether").orElseThrow());
        assertEquals(List.of("minecraft:soul_sand"), PlantTypes.soilsFor("  Nether ").orElseThrow());
    }

    /** A stem that declares neither growsOn nor plantType is vanilla's melon stem. */
    @Test
    void defaultsToFarmland() {
        assertEquals(List.of("minecraft:farmland"),
            PlantTypes.soilsFor(PlantTypes.DEFAULT).orElseThrow());
    }

    /**
     * Cave and Beach asked a question about the block below rather than naming one,
     * and a block tag cannot ask a question.
     */
    @Test
    void reportsTheTwoThatAreConditionsRatherThanLists() {
        assertTrue(PlantTypes.soilsFor("cave").isEmpty());
        assertTrue(PlantTypes.isCondition("cave"));
        assertTrue(PlantTypes.isCondition("Beach"));

        assertTrue(PlantTypes.soilsFor("hovercraft").isEmpty());
        assertFalse(PlantTypes.isCondition("hovercraft"),
            "an unrecognised word is not one of the two, and should be reported as unknown");
    }

    @Test
    void answersNullWithoutThrowing() {
        assertTrue(PlantTypes.soilsFor(null).isEmpty());
        assertFalse(PlantTypes.isCondition(null));
    }
}
