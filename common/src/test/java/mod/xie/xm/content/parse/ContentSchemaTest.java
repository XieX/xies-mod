package mod.xie.xm.content.parse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.TexDef;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps {@code xie-content.schema.json} honest about the things the code decides.
 *
 * <p>There is no JSON Schema validator on the test classpath, so this does not
 * validate content against the schema. It checks the schema is well-formed and that
 * the lists it copies from Java — archetypes, section names, texture faces — have not
 * drifted from their source of truth. Drift the other way (a property the registrar
 * reads that the schema does not describe, or describes under the wrong archetype's
 * {@code allOf} branch) is caught by nothing; keep the two in step by hand when adding
 * one.
 */
class ContentSchemaTest {

    private static JsonObject schema;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = XieParser.class.getResourceAsStream(XieParser.SCHEMA_RESOURCE)) {
            assertNotNull(in, "schema resource " + XieParser.SCHEMA_RESOURCE + " is missing");
            schema = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
    }

    private static JsonObject definition(String name) {
        JsonElement def = schema.getAsJsonObject("definitions").get(name);
        assertNotNull(def, "schema has no definition " + name);
        return def.getAsJsonObject();
    }

    /** The {@code enum} inside the first {@code anyOf} branch of a type definition. */
    private static Set<String> enumOf(String definition) {
        JsonArray branches = definition(definition).getAsJsonArray("anyOf");
        JsonArray values = branches.get(0).getAsJsonObject().getAsJsonArray("enum");
        Set<String> out = new HashSet<>();
        values.forEach(value -> out.add(value.getAsString()));
        return out;
    }

    @Test
    void blockArchetypesMatchBlockDef() {
        assertEquals(BlockDef.KNOWN_ARCHETYPES, enumOf("blockType"));
    }

    @Test
    void itemArchetypesMatchItemDef() {
        assertEquals(ItemDef.KNOWN_ARCHETYPES, enumOf("itemType"));
    }

    @Test
    void everySectionSpellingIsATopLevelProperty() {
        Set<String> top = schema.getAsJsonObject("properties").keySet();
        for (String section : XieFileReader.SECTION_NAMES) {
            assertTrue(top.contains(section), "schema does not list section " + section);
        }
        assertTrue(top.contains("texturePaths"));
        assertTrue(top.contains("version"));
        assertTrue(top.contains("$schema"));
    }

    @Test
    void textureFacesAreListed() {
        Set<String> faces = definition("tex").getAsJsonObject("properties").keySet();
        for (String face : new String[] {TexDef.FACE_ALL, TexDef.FACE_SIDES, TexDef.FACE_ENDS,
            TexDef.FACE_TOP, TexDef.FACE_BOTTOM, TexDef.FACE_FRONT, TexDef.FACE_BACK,
            TexDef.FACE_ICON}) {
            assertTrue(faces.contains(face), "schema tex lacks face " + face);
        }
    }

    @Test
    void unknownTopLevelKeysAreRejectedButUnderscoredOnesAreNot() {
        // The reader warns on an unknown section and skips an underscored one; the
        // schema should say the same so an editor flags a typo before the game does.
        assertEquals(false, schema.get("additionalProperties").getAsBoolean());
        assertTrue(schema.getAsJsonObject("patternProperties").has("^_"));
    }
}
