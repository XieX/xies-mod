package mod.xie.xm.content.parse;

import mod.xie.xm.content.model.AliasDef;
import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.RecipeDef;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Parses the real content repository.
 *
 * <p>This is the test that matters: the inline fixtures prove the parser handles
 * the format as documented, this proves it handles the format as actually written
 * over several years by several people.
 *
 * <p>Skipped when the content repo is not checked out beside this one. Point it
 * elsewhere with {@code -Dxie.content.dir=...}.
 */
class RealContentTest {

    private static Path contentDir;
    private static XieParser.ParseResult result;

    @BeforeAll
    static void parseOnce() throws IOException {
        String configured = System.getProperty("xie.content.dir");
        contentDir = configured != null ? Paths.get(configured) : Paths.get("..", "..", "mod-content");
        assumeTrue(Files.isDirectory(contentDir),
            "content repo not found at " + contentDir.toAbsolutePath() + "; skipping");
        result = XieParser.parsePack(contentDir);

        System.out.println("Parsed " + result.pack());
        System.out.println("Diagnostics: " + result.diagnostics().summary());
        result.diagnostics().all().forEach(d -> System.out.println("  " + d));
    }

    @Test
    void parsesWithoutErrors() {
        assertTrue(result.diagnostics().errors().isEmpty(),
            () -> "content failed to parse:" + System.lineSeparator()
                + String.join(System.lineSeparator(),
                    result.diagnostics().errors().stream().map(Object::toString).toList()));
    }

    @Test
    void findsTheFarmingContent() {
        ContentPack pack = result.pack();
        assertFalse(pack.isEmpty(), "parsed nothing at all");

        BlockDef bush = pack.blocks().get("tomatoBush");
        assertNotNull(bush, "expected tomatoBush from farming/crops/tomato.xie");
        assertEquals("bush", bush.archetype());
        assertFalse(bush.drops().isEmpty(), "tomatoBush should drop something");

        ItemDef tomato = pack.items().get("tomato");
        assertNotNull(tomato, "expected the tomato item");
        assertEquals("food", tomato.archetype());
        assertTrue(tomato.hunger().isPresent());

        assertNotNull(pack.blocks().get("campfire"), "expected campfire from farming/misc");
        assertNotNull(pack.blocks().get("ashpile"), "expected ashpile from farming/misc");
        assertNotNull(pack.items().get("potash"), "expected potash from farming/misc");
    }

    /**
     * Growth-stage counts drive how many blockstates each bush registers, so a
     * regression here silently multiplies every crop's state count.
     */
    @Test
    void infersGrowthStagesFromOpenEndedBounds() {
        var expected = java.util.Map.of(
            "tomatoBush", 4,     // 0-1, 2, 3, 4
            "teaBush", 8,        // 0-2, 3-4, 5-7, 8+
            "roseBush", 4,       // 0-1, 2, 3, 4+
            "cottonPlant", 5,    // 0-1, 2-3, 4, 5+
            // Written with closed ranges up to "14-15", so it really does use the
            // full metadata range — unlike its siblings, which stop at "n+".
            "dandelionBush", 15
        );
        expected.forEach((name, maxAge) -> {
            BlockDef block = result.pack().blocks().get(name);
            assertNotNull(block, "expected block " + name);
            assertEquals(maxAge, block.impliedMaxMeta(),
                name + " should have " + (maxAge + 1) + " growth stages");
        });
    }

    @Test
    void mergesAliasesAcrossFiles() {
        AliasDef salady = result.pack().aliases().get("salady");
        assertNotNull(salady, "expected the salady alias from farming/foods");
        assertTrue(salady.members().size() >= 3,
            "salady should list several interchangeable items, got " + salady.members());
    }

    @Test
    void readsLegacyIdFile() {
        assertEquals(213, result.pack().legacyIds().get("tomatoBush"),
            "expected the id assignment from farming/ids.xid");
    }

    @Test
    void readsRootLevelOverrideFile() {
        BlockDef melon = result.pack().blocks().get("melon");
        assertNotNull(melon, "expected the melon override from MelonOverride.xie");
        assertTrue(melon.isOverride());
        assertEquals("tile.melon", melon.override().orElseThrow().name());
    }

    @Test
    void skipsUnderscorePrefixedDirectories() {
        boolean fromSkipped = result.pack().blocks().values().stream()
            .anyMatch(block -> block.origin().path().contains("_wip")
                || block.origin().path().contains("_plox_update"));
        assertFalse(fromSkipped, "work-in-progress directories should not be loaded");
    }

    /**
     * Not an assertion so much as a report: recipes with structural problems are
     * worth seeing, but the goal of this step is a faithful parse, not a clean
     * content repo.
     */
    @Test
    void reportsStructurallyInvalidRecipes() {
        int broken = 0;
        for (RecipeDef recipe : result.pack().recipes()) {
            for (String problem : recipe.problems()) {
                System.out.println("recipe " + recipe.origin() + ": " + problem);
                broken++;
            }
        }
        System.out.println(broken + " recipe problems across "
            + result.pack().recipes().size() + " recipes");
    }

    /**
     * The {@code .xie} files still address textures as spritesheet indices, but the
     * texture tree is already one PNG per sprite, named after the definition with an
     * optional {@code _<meta>} suffix. If that convention covers everything, the
     * sheet indices are dead metadata and no slicer is needed for the port.
     */
    @Test
    void reportsTextureCoverageByNamingConvention() throws IOException {
        var blockTextures = baseNames(contentDir.resolve("textures").resolve("blocks"));
        var itemTextures = baseNames(contentDir.resolve("textures").resolve("items"));

        var missingBlocks = result.pack().blocks().keySet().stream()
            .filter(name -> !containsIgnoringCase(blockTextures, name))
            .sorted().toList();
        var missingItems = result.pack().items().keySet().stream()
            .filter(name -> !containsIgnoringCase(itemTextures, name))
            .sorted().toList();

        System.out.println("blocks with a matching texture: "
            + (result.pack().blocks().size() - missingBlocks.size())
            + "/" + result.pack().blocks().size() + "  missing: " + missingBlocks);
        System.out.println("items with a matching texture:  "
            + (result.pack().items().size() - missingItems.size())
            + "/" + result.pack().items().size() + "  missing: " + missingItems);

        var unusedBlocks = new java.util.TreeSet<>(blockTextures);
        result.pack().blocks().keySet().forEach(name -> removeIgnoringCase(unusedBlocks, name));
        var unusedItems = new java.util.TreeSet<>(itemTextures);
        result.pack().items().keySet().forEach(name -> removeIgnoringCase(unusedItems, name));
        System.out.println("textures with no definition, blocks: " + unusedBlocks);
        System.out.println("textures with no definition, items:  " + unusedItems);
    }

    /** Texture basenames with the extension and any {@code _<meta>} suffix removed. */
    private static java.util.SortedSet<String> baseNames(Path dir) throws IOException {
        var names = new java.util.TreeSet<String>();
        if (!Files.isDirectory(dir)) {
            return names;
        }
        try (var files = Files.list(dir)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String name = file.getFileName().toString();
                if (!name.toLowerCase().endsWith(".png")) {
                    return;
                }
                name = name.substring(0, name.length() - 4).replaceAll("_\\d+$", "");
                if (!name.isEmpty()) {
                    names.add(name);
                }
            });
        }
        return names;
    }

    private static boolean containsIgnoringCase(java.util.Set<String> names, String target) {
        return names.stream().anyMatch(name -> name.equalsIgnoreCase(target));
    }

    private static void removeIgnoringCase(java.util.Set<String> names, String target) {
        names.removeIf(name -> name.equalsIgnoreCase(target));
    }

    /** Every spritesheet a definition names should exist under textures/. */
    @Test
    void reportsMissingSpritesheets() {
        for (String sheet : result.pack().referencedSheets()) {
            boolean found;
            try (var walk = Files.walk(contentDir)) {
                found = walk.anyMatch(p -> p.getFileName().toString().equalsIgnoreCase(sheet));
            } catch (IOException e) {
                found = false;
            }
            if (!found) {
                System.out.println("referenced but not found: " + sheet);
            }
        }
    }
}
