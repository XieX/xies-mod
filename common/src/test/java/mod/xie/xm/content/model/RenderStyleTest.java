package mod.xie.xm.content.model;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderStyleTest {

    private static BlockDef block(String archetype, String renderType) {
        JsonObject json = new JsonObject();
        if (renderType != null) {
            json.addProperty("renderType", renderType);
        }
        return new BlockDef(new Origin("test", "test.xie", "b"), "b", "B", archetype,
            Optional.empty(), new DropTable(List.of()), DropTable.EMPTY, TexDef.EMPTY,
            Optional.empty(), PropertyMap.of(json));
    }

    @Test
    void mapsTheNamesTheOriginalRecognised() {
        assertEquals(RenderStyle.CROSS, RenderStyle.parse("flower"));
        assertEquals(RenderStyle.CROSS, RenderStyle.parse("billboard"));
        assertEquals(RenderStyle.CROSS, RenderStyle.parse("sapling"));
        assertEquals(RenderStyle.CROP, RenderStyle.parse("crops"));
        assertEquals(RenderStyle.CUBE, RenderStyle.parse("normal"));
    }

    /** {@code Xie.parseRenderType} fell through to 0 rather than failing. */
    @Test
    void anUnrecognisedNameIsACube() {
        assertEquals(RenderStyle.CUBE, RenderStyle.parse("wibble"));
        assertEquals(RenderStyle.CUBE, RenderStyle.parse(null));
    }

    @Test
    void isCaseAndSpaceInsensitive() {
        assertEquals(RenderStyle.CROSS, RenderStyle.parse("  Flower "));
        assertEquals(RenderStyle.CROP, RenderStyle.parse("CROP"));
    }

    @Test
    void fallsBackToTheArchetype() {
        assertEquals(RenderStyle.CROSS, RenderStyle.forBlock(block("bush", null)));
        assertEquals(RenderStyle.CROSS, RenderStyle.forBlock(block("plant", null)));
        assertEquals(RenderStyle.CROP, RenderStyle.forBlock(block("crop", null)));
        assertEquals(RenderStyle.CUBE, RenderStyle.forBlock(block("basic", null)));
    }

    /**
     * The property beats the archetype, which is the whole reason for having it:
     * {@code shrub} is a {@code basic} block drawn as a cross.
     */
    @Test
    void theDeclaredTypeWinsOverTheArchetype() {
        assertEquals(RenderStyle.CROSS, RenderStyle.forBlock(block("basic", "flower")));
        assertEquals(RenderStyle.CUBE, RenderStyle.forBlock(block("bush", "normal")));
    }
}
