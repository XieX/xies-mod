package mod.xie.xm.cook;

import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.parse.XieParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks that {@code cooking/cookpot.json} says what {@link StovePot} assumes it says.
 *
 * <p>The two halves of the stove can drift apart silently: the state machine names
 * six meals by id path, and the content file is what registers items at those paths.
 * Nothing else would catch a rename until the block produced an air stack in-game.
 */
class StoveContentTest {

    private static ContentPack pack;

    @BeforeAll
    static void parse() throws IOException {
        String configured = System.getProperty("xie.content.dir");
        Path contentDir = configured != null ? Paths.get(configured) : Paths.get("..", "..", "mod-content");
        assumeTrue(Files.isDirectory(contentDir),
            "content repo not found at " + contentDir.toAbsolutePath() + "; skipping");

        pack = XieParser.parsePack(contentDir).pack();
        assumeTrue(pack.blocks().containsKey("cookpot"),
            "no cookpot declared in " + contentDir.toAbsolutePath() + "; skipping");
    }

    @Test
    void theStoveIsAStove() {
        BlockDef stove = pack.blocks().get("cookpot");
        assertEquals("stove", stove.archetype());
        assertEquals("Cookpot", stove.displayName());
    }

    @Test
    void everyMealTheStateMachineNamesIsARegisteredItem() {
        for (StovePot.Meal meal : StovePot.Meal.values()) {
            Optional<ItemDef> item = pack.items().values().stream()
                .filter(def -> pathOf(def).equals(meal.path()))
                .findFirst();

            assertTrue(item.isPresent(),
                "StovePot names " + meal + " at id path \"" + meal.path()
                    + "\" but no item in the content registers that path");
            assertEquals("food", item.get().archetype(),
                meal + " should be a food item");
        }
    }

    @Test
    void theMealsCarryTheAgreedFoodValues() {
        assertHunger("weak_soup", 4);
        assertHunger("soup", 6);
        assertHunger("stew", 8);
    }

    @Test
    void theIngredientAliasesExist() {
        assertTrue(pack.aliases().containsKey("soupable"),
            "the food ingredient list is the $soupable alias");
        assertTrue(pack.aliases().containsKey("flavouring"),
            "the flavouring list is the $flavouring alias");

        assertTrue(pack.aliases().get("flavouring").members().stream()
                .anyMatch(ref -> ref.name().equalsIgnoreCase("salt")),
            "salt is the flavouring the mod posting names first");
    }

    private void assertHunger(String path, int expected) {
        ItemDef def = pack.items().values().stream()
            .filter(item -> pathOf(item).equals(path))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no item at id path " + path));

        assertEquals(expected, def.hunger().orElse(-1), path + " nutrition");
    }

    /** The id path a definition registers at, e.g. {@code weakSoup} to {@code weak_soup}. */
    private static String pathOf(ItemDef def) {
        return mod.xie.xm.content.model.ResourceNames.toPath(def.name());
    }
}
