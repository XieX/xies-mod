package mod.xie.xm.content.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledContentTest {

    /** A classpath in a map: resource name to content. */
    private static Function<String, InputStream> classpath(Map<String, String> files) {
        return name -> {
            String body = files.get(name);
            return body == null ? null : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        };
    }

    private static Map<String, String> bundle(String version) {
        Map<String, String> files = new HashMap<>();
        files.put(BundledContent.VERSION_RESOURCE, version + "\n");
        files.put(BundledContent.INDEX_RESOURCE,
            "farming/tomato.json\n\ntextures/blocks/tomato_bush.png\nLICENSE\n");
        files.put("/bundled/base-content/farming/tomato.json", "{\"blocks\":{}}");
        files.put("/bundled/base-content/textures/blocks/tomato_bush.png", "png");
        files.put("/bundled/base-content/LICENSE", "CC BY 4.0");
        return files;
    }

    @Test
    void installsEveryIndexedFileAndAMarker(@TempDir Path container) throws IOException {
        BundledContent.Result result = BundledContent.install(container, classpath(bundle("0.4.0")));

        assertEquals(BundledContent.Outcome.INSTALLED, result.outcome());
        assertEquals(3, result.files());
        assertEquals("0.4.0", result.bundledVersion());
        assertNull(result.installedVersion());

        Path pack = container.resolve("base-content");
        assertEquals("{\"blocks\":{}}", Files.readString(pack.resolve("farming/tomato.json")));
        assertEquals("png", Files.readString(pack.resolve("textures/blocks/tomato_bush.png")));
        assertEquals("CC BY 4.0", Files.readString(pack.resolve("LICENSE")));
        assertEquals("0.4.0", Files.readString(pack.resolve(BundledContent.MARKER_FILE)).trim());

        // The staging directory is gone, so nothing with an underscore is left
        // beside the pack for a user to wonder about.
        try (var entries = Files.list(container)) {
            assertEquals(1, entries.count());
        }
    }

    @Test
    void neverTouchesAPackThatIsAlreadyThere(@TempDir Path container) throws IOException {
        BundledContent.install(container, classpath(bundle("0.4.0")));
        Path definition = container.resolve("base-content/farming/tomato.json");
        Files.delete(definition);

        BundledContent.Result again = BundledContent.install(container, classpath(bundle("0.4.0")));

        assertEquals(BundledContent.Outcome.PRESENT, again.outcome());
        assertEquals(0, again.files());
        assertEquals("0.4.0", again.installedVersion());
        assertFalse(Files.exists(definition), "a deleted file is an opt-out and must stay deleted");
    }

    @Test
    void reportsAnOlderInstallWithoutReplacingIt(@TempDir Path container) throws IOException {
        BundledContent.install(container, classpath(bundle("0.4.0")));

        BundledContent.Result newer = BundledContent.install(container, classpath(bundle("0.5.0")));

        assertEquals(BundledContent.Outcome.OUTDATED, newer.outcome());
        assertEquals("0.5.0", newer.bundledVersion());
        assertEquals("0.4.0", newer.installedVersion());
        assertEquals("0.4.0",
            Files.readString(container.resolve("base-content/" + BundledContent.MARKER_FILE)).trim());
    }

    @Test
    void leavesAUserMadePackAloneWhateverTheVersion(@TempDir Path container) throws IOException {
        Path pack = container.resolve("base-content");
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("mine.json"), "{}");

        BundledContent.Result result = BundledContent.install(container, classpath(bundle("0.4.0")));

        assertEquals(BundledContent.Outcome.PRESENT, result.outcome());
        assertNull(result.installedVersion(), "no marker means nobody installed it from a jar");
        assertFalse(Files.exists(pack.resolve(BundledContent.MARKER_FILE)));
    }

    @Test
    void saysSoWhenNothingIsBundled(@TempDir Path container) throws IOException {
        BundledContent.Result result = BundledContent.install(container, classpath(Map.of()));

        assertEquals(BundledContent.Outcome.NOT_BUNDLED, result.outcome());
        assertFalse(Files.exists(container.resolve("base-content")));
    }

    @Test
    void refusesAnIndexThatNamesAMissingFile(@TempDir Path container) {
        Map<String, String> broken = bundle("0.4.0");
        broken.remove("/bundled/base-content/LICENSE");

        assertThrows(IOException.class, () -> BundledContent.install(container, classpath(broken)));
        assertFalse(Files.exists(container.resolve("base-content")),
            "a failed install must not leave a pack the parser would load");
    }

    @Test
    void refusesAnIndexThatEscapesThePack(@TempDir Path container) {
        Map<String, String> hostile = bundle("0.4.0");
        hostile.put(BundledContent.INDEX_RESOURCE, "../outside.json\n");
        hostile.put("/bundled/base-content/../outside.json", "{}");

        assertThrows(IOException.class, () -> BundledContent.install(container, classpath(hostile)));
        assertTrue(Files.notExists(container.resolve("outside.json")));
    }
}
