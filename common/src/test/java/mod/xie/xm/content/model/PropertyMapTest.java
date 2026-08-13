package mod.xie.xm.content.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyMapTest {

    private static PropertyMap props(String json) {
        return PropertyMap.of(new Gson().fromJson(json, JsonObject.class));
    }

    /** {@code ultimatefist.xie}, which is the one declaration of the property. */
    private static final String ULTIMATE_FIST = """
        { "transforms": { "stone": "goldBlock", "dirt": "air" } }
        """;

    @Test
    void readsATransformsMap() {
        assertEquals(Map.of("stone", "goldBlock", "dirt", "air"),
            props(ULTIMATE_FIST).getStringMap("transforms"));
    }

    @Test
    void keepsDeclarationOrder() {
        // The order entries are tried in, and the first match wins. A HashMap in
        // 2013 made that arbitrary; writing it down makes it repeatable.
        assertEquals(List.of("stone", "dirt"),
            List.copyOf(props(ULTIMATE_FIST).getStringMap("transforms").keySet()));
    }

    @Test
    void keepsTheCaseOfBlockNames() {
        // Property names are folded to lower case; these are block names and cannot
        // be, or goldBlock stops resolving to gold_block.
        assertTrue(props(ULTIMATE_FIST).getStringMap("transforms").containsValue("goldBlock"));
    }

    @Test
    void readsNothingFromTheShapesThatAreNotAMap() {
        // A bare string was the 2013 wildcard form, which matched no block at all.
        assertTrue(props("{ \"transforms\": \"goldBlock\" }").getStringMap("transforms").isEmpty());
        assertTrue(props("{ \"name\": \"x\" }").getStringMap("transforms").isEmpty());
    }

    @Test
    void reportsADeclaredPropertyEvenWhenItReadsAsNothing() {
        // How the registrar tells "declared nothing" from "declared something
        // unreadable", which is worth a warning.
        PropertyMap props = props("{ \"transforms\": \"goldBlock\" }");
        assertTrue(props.getStringMap("transforms").isEmpty());
        assertTrue(props.has("transforms"));
    }

    @Test
    void ignoresEntriesThatAreNotStrings() {
        assertEquals(Map.of("stone", "goldBlock"),
            props("{ \"transforms\": { \"stone\": \"goldBlock\", \"dirt\": [1, 2] } }")
                .getStringMap("transforms"));
    }

    @Test
    void marksTheKeyRead() {
        PropertyMap props = props(ULTIMATE_FIST);
        props.getStringMap("transforms");
        assertTrue(props.untouchedKeys().isEmpty());
    }
}
