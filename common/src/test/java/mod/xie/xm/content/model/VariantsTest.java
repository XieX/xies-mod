package mod.xie.xm.content.model;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariantsTest {

    private static PropertyMap props(String names) {
        JsonObject json = new JsonObject();
        if (names != null) {
            json.addProperty("names", names);
        }
        return PropertyMap.of(json);
    }

    @Test
    void readsSubtypeNames() {
        assertEquals(
            List.of("Apple Seeds", "Orange Seeds", "Lemon Seeds", "Avocado Seed"),
            Variants.namesIn(props("Apple Seeds,Orange Seeds,Lemon Seeds,Avocado Seed")));
    }

    @Test
    void aDefinitionWithoutNamesIsNotSplit() {
        assertTrue(Variants.namesIn(props(null)).isEmpty());
        assertFalse(Variants.isSplit(props(null)));
    }

    /** One name describes the same single object the definition would be anyway. */
    @Test
    void aSingleNameIsNotSplit() {
        assertFalse(Variants.isSplit(props("Oil")));
        assertEquals(List.of("oil"), Variants.pathsFor("oil", Variants.namesIn(props("Oil"))));
    }

    @Test
    void stopsAtTheLegacyMetadataCeiling() {
        String sixteen = String.join(",", java.util.Collections.nCopies(20, "Thing"));
        assertEquals(Variants.MAX_SUBTYPES, Variants.namesIn(props(sixteen)).size());
    }

    @Test
    void derivesOnePathPerSubtype() {
        List<String> names = Variants.namesIn(props("Cider,Orange Juice,Lemonade,Tomato Juice"));
        assertEquals(
            List.of("cider", "orange_juice", "lemonade", "tomato_juice"),
            Variants.pathsFor("fruitJuice", names));
    }

    /**
     * The property the whole approach rests on: the alias a pack declares for a
     * subtype reduces to the same path as the subtype's display name, so a
     * reference written either way lands on the same object.
     */
    @Test
    void derivedPathsMatchTheAliasesTheContentDeclares() {
        List<String> names = Variants.namesIn(
            props("Apple Seeds,Orange Seeds,Lemon Seeds,Avocado Seed"));
        List<String> aliases = List.of("appleSeeds", "orangeSeeds", "lemonSeeds", "avocadoSeed");

        for (int meta = 0; meta < aliases.size(); meta++) {
            assertEquals(
                ResourceNames.toPath(aliases.get(meta)),
                Variants.pathFor("fruitSeed", names, meta),
                "alias " + aliases.get(meta) + " should resolve to subtype " + meta);
        }
    }

    /** A definition with no subtypes still has exactly one path: its own. */
    @Test
    void anUnsplitDefinitionKeepsItsOwnPath() {
        assertEquals(List.of("tomato_bush"), Variants.pathsFor("tomatoBush", List.of()));
    }

    @Test
    void fallsBackToTheDefinitionNameWhenADisplayNameYieldsNothing() {
        List<String> names = Variants.namesIn(props("Oil, ??? ,Corn Oil"));
        assertEquals(List.of("oil", "oil_1", "corn_oil"), Variants.pathsFor("oil", names));
    }

    @Test
    void aMetaOutsideTheDeclaredNamesFallsBack() {
        List<String> names = Variants.namesIn(props("Oil,Corn Oil"));
        assertEquals("oil_7", Variants.pathFor("oil", names, 7));
    }
}
