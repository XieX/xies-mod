package mod.xie.xm.content.parse;

import mod.xie.xm.content.model.ContentPack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which files in a pack directory are definition files, and what the reader makes of them. */
class XieParserTest {

    @TempDir
    Path root;

    private static final String ONE_ITEM = """
        { "items": { "%s": { "name": "%s", "type": "food", "hunger": 1, "nourishment": 0.1 } } }
        """;

    private void write(String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private ContentPack parse() throws IOException {
        XieParser.ParseResult result = XieParser.parsePack(root);
        assertTrue(result.diagnostics().errors().isEmpty(), result.diagnostics().toString());
        return result.pack();
    }

    @Test
    void readsJsonAndXieAlike() throws IOException {
        write("foods/apple.json", ONE_ITEM.formatted("apple", "Apple"));
        write("foods/pear.xie", ONE_ITEM.formatted("pear", "Pear"));
        write("foods/notes.txt", "not content");

        ContentPack pack = parse();

        // ContentPack copies its maps with Map.copyOf, whose iteration order is
        // randomised per JVM run, so only membership is a contract here.
        assertEquals(Set.of("apple", "pear"), pack.items().keySet());
        assertEquals("apple", pack.items().get("apple").origin().file(),
            "the basename, not the extension, is what parent@name references");
    }

    @Test
    void extensionIsCaseInsensitive() throws IOException {
        write("Apple.JSON", ONE_ITEM.formatted("apple", "Apple"));
        assertTrue(parse().items().containsKey("apple"));
    }

    @Test
    void schemaPointerIsNotASection() throws IOException {
        write("apple.json", """
            {
              "$schema": "../xie-content.schema.json",
              "items": { "apple": { "name": "Apple", "type": "food", "hunger": 1, "nourishment": 0.1 } }
            }
            """);

        XieParser.ParseResult result = XieParser.parsePack(root);

        assertTrue(result.pack().items().containsKey("apple"));
        assertFalse(result.diagnostics().toString().contains("unknown section"),
            result.diagnostics().toString());
    }

    @Test
    void installsTheSchemaNextToThePacks() throws IOException {
        Path container = root.resolve("xiecontent");

        Path written = XieParser.installSchema(container);

        assertEquals(container.resolve(XieParser.SCHEMA_FILE_NAME), written);
        assertTrue(Files.readString(written).contains("\"title\": \"Xie content file\""));
        // A pack dropped beside it must not pick the schema up as a definition file.
        write("xiecontent/mypack/apple.json", ONE_ITEM.formatted("apple", "Apple"));
        XieParser.ParseResult result = XieParser.parseAll(container).get(0);
        assertEquals(List.of("apple"), List.copyOf(result.pack().items().keySet()));
    }

    @Test
    void underscoredFilesAreSkippedWhateverTheExtension() throws IOException {
        write("_wip/apple.json", ONE_ITEM.formatted("apple", "Apple"));
        write("_pear.json", ONE_ITEM.formatted("pear", "Pear"));
        assertTrue(parse().items().isEmpty());
    }
}
