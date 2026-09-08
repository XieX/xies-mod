package mod.xie.xm.content.model;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TreeShapeTest {

    private static BlockDef tree(String shape) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "tree");
        if (shape != null) {
            json.addProperty("shape", shape);
        }
        return new BlockDef(new Origin("test", "test.json", "leaves"), "leaves", "Leaves", "tree",
            Optional.empty(), new DropTable(List.of()), DropTable.EMPTY, TexDef.EMPTY,
            Optional.empty(), PropertyMap.of(json));
    }

    @Test
    void mapsTheNamesTheOriginalRecognised() {
        assertEquals(TreeShape.CONIFER, TreeShape.parse("conifer"));
        assertEquals(TreeShape.CONIFER, TreeShape.parse("pine"));
        assertEquals(TreeShape.TALL, TreeShape.parse("tall"));
        assertEquals(TreeShape.TALL, TreeShape.parse("tallpine"));
        assertEquals(TreeShape.TALL, TreeShape.parse("tallconifer"));
        assertEquals(TreeShape.OAK, TreeShape.parse("oak"));
    }

    /**
     * {@code TreeGenerator.getShape} gave birch a name of its own and
     * {@code XieBlockLeaves.generate} then sent it to {@code generateOak}, so the two
     * are the same tree and there is nothing for a fourth constant to hold.
     */
    @Test
    void birchIsAnOak() {
        assertEquals(TreeShape.OAK, TreeShape.parse("birch"));
    }

    @Test
    void anUnrecognisedNameIsAnOak() {
        assertEquals(TreeShape.OAK, TreeShape.parse("wibble"));
        assertEquals(TreeShape.OAK, TreeShape.parse(null));
    }

    @Test
    void isCaseAndSpaceInsensitive() {
        assertEquals(TreeShape.CONIFER, TreeShape.parse("  Pine "));
        assertEquals(TreeShape.TALL, TreeShape.parse("TALLPINE"));
    }

    /** Nothing in the live content declares one, so the default has to be the oak. */
    @Test
    void aDefinitionWithNoShapeIsAnOak() {
        assertEquals(TreeShape.OAK, TreeShape.forBlock(tree(null)));
        assertEquals(TreeShape.CONIFER, TreeShape.forBlock(tree("conifer")));
    }
}
