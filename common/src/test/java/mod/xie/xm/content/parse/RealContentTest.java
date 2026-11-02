package mod.xie.xm.content.parse;

import mod.xie.xm.content.model.AliasDef;
import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.ItemRef;
import mod.xie.xm.content.model.RecipeDef;
import mod.xie.xm.content.model.SeedReturns;
import mod.xie.xm.content.model.SpawnDef;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
        assertNotNull(bush, "expected tomatoBush from farming/crops/tomato.json");
        assertEquals("bush", bush.archetype());
        assertFalse(bush.drops().isEmpty(), "tomatoBush should drop something");

        ItemDef tomato = pack.items().get("tomato");
        assertNotNull(tomato, "expected the tomato item");
        assertEquals("food", tomato.archetype());
        assertTrue(tomato.hunger().isPresent());

        assertNotNull(pack.blocks().get("saltDeposit"), "expected saltDeposit from farming/misc");
        assertNotNull(pack.items().get("salt"), "expected salt from farming/misc");
        assertNotNull(pack.items().get("sammich"), "expected sammich from farming/foods");
    }

    /**
     * {@code fruit} is written exactly like {@code drops} and means something else,
     * so it is parsed by the same code into a field of its own. Getting the two
     * confused would make a fruit tree drop its whole harvest when you break a leaf.
     */
    @Test
    void readsFruitSeparatelyFromDrops() {
        BlockDef leaves = result.pack().blocks().get("fruitLeaves");
        assertNotNull(leaves, "expected fruitLeaves from farming/trees/fruit.json");
        assertEquals("tree", leaves.archetype());

        assertEquals(List.of("apple", "orange", "lemon", "avocado"),
            leaves.fruit().drops().stream().map(drop -> drop.item().name()).toList());
        assertEquals(List.of("apple", "orange", "lemon", "avocado"),
            leaves.drops().drops().stream().map(drop -> drop.item().name()).toList());

        // Metadata picks the subtype here rather than a growth stage, so each fruit
        // belongs to exactly one of the four trees.
        var orange = leaves.fruit().drops().get(1);
        assertEquals(100, orange.chanceFor(1), "the orange tree bears oranges");
        assertEquals(0, orange.chanceFor(0), "the apple tree does not");
        assertTrue(result.pack().blocks().get("fruitSapling").fruit().isEmpty(),
            "a sapling bears nothing");
    }

    /** What the emitter warns about, held at none: no crop pays for its seed unripe. */
    @Test
    void noCropGivesBackItsSeedTheMomentItIsPlanted() {
        List<String> found = SeedReturns.in(result.pack().blocks(), result.pack().items()).stream()
            .map(finding -> finding.block().qualifiedName() + " drops " + finding.perBreak()
                + " " + finding.seed().name() + " at age 0")
            .toList();
        assertTrue(found.isEmpty(), found::toString);
    }

    /**
     * The four fruit trees are named by alias, and each alias has to reach a
     * different subtype. Written as {@code fruitLeaves:0} through {@code :3} they
     * would all reach the first, because a two-part reference is a count and not a
     * metadata value — which is what they said until this port, and why every one of
     * the four spawn entries generated an apple tree in 2013.
     */
    @Test
    void theTreeAliasesReachAllFourSubtypes() {
        var expected = java.util.Map.of(
            "appleTree", 0, "orangeTree", 1, "lemonTree", 2, "avocadoTree", 3);
        expected.forEach((name, meta) -> {
            AliasDef alias = result.pack().aliases().get(name);
            assertNotNull(alias, "expected the " + name + " alias");
            ItemRef target = alias.members().get(0);
            assertEquals("fruitLeaves", target.name());
            assertTrue(target.hasMeta(), name + " must name a subtype, not just a count");
            assertEquals(meta, target.meta());
        });
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

    /**
     * The two {@code spawns} sections are spelled differently — {@code wildshrubs.json}
     * and {@code fruit.json} say {@code "spawn"} and {@code salt.json} says
     * {@code "spawns"} — and one keys its entries by alias while the other names the
     * block in a property. Both spellings and both forms have to reach the emitter.
     */
    @Test
    void readsBothSpellingsOfTheSpawnSection() {
        List<SpawnDef> spawns = result.pack().spawns();
        assertEquals(10, spawns.size(), "expected every spawn entry in the content repo");

        SpawnDef named = byName(spawns, "seasalt");
        assertEquals("saltDeposit", named.target().name(),
            "a \"spawns\" entry names its block in a property");
        assertTrue(named.clump());
        assertEquals(16, named.clusterSize());
        assertEquals(2, named.nearDistance());
        assertEquals(List.of("8", "9"), named.spawnsNear().stream().map(ItemRef::name).toList(),
            "spawnsNear is written as 1.6 numeric block ids");

        SpawnDef byAlias = byName(spawns, "wildCorn");
        assertEquals("wildCorn", byAlias.target().name(),
            "a \"spawn\" entry keyed by alias takes its own key as the block");
        assertEquals(22, byAlias.rarity());
        assertEquals(5, byAlias.clusterSize());
        assertTrue(byAlias.clump());

        // Nothing declares one, so every entry generates in the surface pass.
        assertTrue(spawns.stream().allMatch(spawn -> spawn.spawnType().equals("surface")));
        assertTrue(spawns.stream().allMatch(SpawnDef::generates));
    }

    /**
     * Defaults come from {@code Spawnable}'s field initialisers rather than from
     * anything the format documents, and {@code wildTomato} declares neither.
     */
    @Test
    void fallsBackToTheOriginalsDefaults() {
        SpawnDef wildTomato = byName(result.pack().spawns(), "wildTomato");
        assertEquals(1, wildTomato.clusterSize(), "one block unless clusterSize says otherwise");
        assertFalse(wildTomato.clump());
        assertEquals(6, wildTomato.nearDistance(), "Spawnable.nearDistance starts at 6");
    }

    private static SpawnDef byName(List<SpawnDef> spawns, String name) {
        return spawns.stream().filter(spawn -> spawn.name().equals(name)).findFirst()
            .orElseThrow(() -> new AssertionError("no spawn entry named " + name));
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
    void omitsContentVanillaHasSince() {
        ContentPack pack = result.pack();
        // Each of these was removed because Minecraft gained an equivalent after
        // 1.6, and reintroducing one would mean shipping a worse duplicate of a
        // vanilla feature. Campfire arrived in 1.14, the composter replaced the
        // potash-and-fertiliser loop in the same release, and vanilla melon
        // mechanics no longer need overriding.
        for (String block : new String[] {"campfire", "ashpile", "melon"}) {
            assertFalse(pack.blocks().containsKey(block),
                block + " is superseded by vanilla and should not be declared");
        }
        for (String item : new String[] {"potash", "fertiliser", "foragingStick",
                                          "pumpkinPiece", "pumpkinCooked", "melon"}) {
            assertFalse(pack.items().containsKey(item),
                item + " is superseded by vanilla and should not be declared");
        }
    }

    @Test
    void skipsUnderscorePrefixedDirectories() {
        boolean fromSkipped = result.pack().blocks().values().stream()
            .anyMatch(block -> block.origin().path().contains("_wip")
                || block.origin().path().contains("_plox_update"));
        assertFalse(fromSkipped, "work-in-progress directories should not be loaded");
    }

    /**
     * The two live {@code cull} entries, pinned because the shape of what they name
     * is easy to misread. {@code redDye:2} is a two-part reference, which
     * {@code Xie.parseItemStack} read as name and <em>count</em> — so it is two red
     * dye, not dye subtype two, and the item is decided by the name alone. Anything
     * that treated the second field as metadata would cull the wrong item entirely.
     */
    @Test
    void parsesCullsAsAnOutputStackWithACount() {
        List<RecipeDef> culls = result.pack().recipes().stream()
            .filter(recipe -> recipe.kind() == RecipeDef.Kind.CULL)
            .toList();
        assertEquals(2, culls.size(), "flowerbushes.json declares both live culls");

        List<ItemRef> targets = culls.stream().flatMap(cull -> cull.culls().stream()).toList();
        assertEquals(List.of("redDye", "yellowDye"), targets.stream().map(ItemRef::name).toList());
        for (ItemRef target : targets) {
            assertEquals(2, target.count(), () -> target + " names a stack of two");
            assertFalse(target.hasMeta(), () -> target + " has no metadata value");
        }
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
     * The definition files still address textures as spritesheet indices, but the
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
