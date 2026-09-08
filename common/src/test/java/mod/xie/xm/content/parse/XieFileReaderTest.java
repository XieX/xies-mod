package mod.xie.xm.content.parse;

import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.Bounds;
import mod.xie.xm.content.model.DropTable;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.ItemRef;
import mod.xie.xm.content.model.RecipeDef;
import mod.xie.xm.content.model.TexDef;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixtures here are trimmed copies of real files from the content repo, so these
 * tests double as documentation of the format.
 */
class XieFileReaderTest {

    private Diagnostics diagnostics;

    private XieFileReader.Result read(String json) {
        diagnostics = new Diagnostics();
        return new XieFileReader(diagnostics).read("test.json", "test", new StringReader(json));
    }

    // -- tomato.json -----------------------------------------------------------

    private static final String TOMATO = """
        {
          "blocks": {
            "tomatoBush": {
              "name": "Tomato Bush",
              "type": "bush",
              "growsOn": "farmland,dirt,grass",
              "drops": {
                "tomato": { "chance": { "0-3": 0, "4+": 50 }, "amount": 2 },
                "tomatoSeeds": { "chance": { "0": 0, "1": 20 }, "amount": 3 }
              },
              "blockBounds": {
                "0-1": "0.4375,0,0.4375,0.6875,0.3125,0.6875",
                "4": "0.125,0,0.125,0.9375,1,0.9375"
              }
            }
          },
          "items": {
            "tomato": {
              "name": "Tomato", "type": "food", "hunger": 4, "nourishment": 0.3,
              "texFile": "xie_crops.png", "icon": 22
            },
            "tomatoSeeds": {
              "name": "Tomato Seeds", "type": "seeds", "growsInto": "tomatoBush",
              "texFile": "xie_crops.png", "icon": 21
            }
          },
          "recipes": { "tomatoSeeds": "tomato" }
        }
        """;

    @Test
    void readsBlockWithDropsAndBounds() {
        XieFileReader.Result result = read(TOMATO);
        assertTrue(diagnostics.errors().isEmpty(), diagnostics::toString);

        BlockDef bush = result.blocks().get("tomatoBush");
        assertNotNull(bush);
        assertEquals("Tomato Bush", bush.displayName());
        assertEquals("bush", bush.archetype());
        assertEquals("test@tomatoBush", bush.qualifiedName());
        assertEquals(List.of("farmland", "dirt", "grass"), bush.props().getStringList("growsOn"));

        DropTable.Drop tomato = bush.drops().drops().stream()
            .filter(d -> d.item().name().equals("tomato"))
            .findFirst().orElseThrow();
        assertEquals(2, tomato.amount());
        assertEquals(0, tomato.chanceFor(0), "unripe bush drops nothing");
        assertEquals(0, tomato.chanceFor(3));
        assertEquals(50, tomato.chanceFor(4), "ripe bush drops at 50%");
        assertFalse(tomato.isUnconditional());

        Bounds young = bush.bounds().orElseThrow().get(0).orElseThrow();
        assertEquals(0.4375f, young.minX(), 1e-6);
        assertEquals(0.3125f, young.maxY(), 1e-6);
        Bounds ripe = bush.bounds().orElseThrow().get(4).orElseThrow();
        assertEquals(1.0f, ripe.maxY(), 1e-6);

        assertEquals(4, bush.impliedMaxMeta());
    }

    @Test
    void readsItemsWithSpritesheetIcons() {
        XieFileReader.Result result = read(TOMATO);

        ItemDef tomato = result.items().get("tomato");
        assertNotNull(tomato);
        assertEquals("food", tomato.archetype());
        assertEquals(4, tomato.hunger().orElseThrow());
        assertEquals(0.3f, tomato.nourishment().orElseThrow(), 1e-6);
        assertEquals("xie_crops.png", tomato.tex().sheet().orElseThrow());
        assertEquals(22, tomato.tex().spriteFor(TexDef.FACE_ICON, 0).orElseThrow());

        ItemDef seeds = result.items().get("tomatoSeeds");
        assertEquals("seeds", seeds.archetype());
        assertEquals("tomatoBush", seeds.props().getString("growsInto").orElseThrow());
    }

    @Test
    void readsShorthandShapelessRecipe() {
        XieFileReader.Result result = read(TOMATO);
        assertEquals(1, result.recipes().size());

        RecipeDef recipe = result.recipes().get(0);
        assertEquals(RecipeDef.Kind.SHAPELESS, recipe.kind());
        assertEquals("tomatoSeeds", recipe.product().orElseThrow().name());
        assertEquals(List.of("tomato"), recipe.ingredients().stream().map(ItemRef::name).toList());
        assertTrue(recipe.problems().isEmpty());
    }

    // -- _campfire.json -------------------------------------------------------

    private static final String CAMPFIRE = """
        {
          "blocks": {
            "campfire": {
              "name": "Campfire", "type": "furnace", "naked": true,
              "material": "wood", "cookTime": 400, "initialBurnTime": 800,
              "burnLevels": "1,2,0,3,4",
              "fuels": { "stick": 200, "log": 1200 },
              "smelts": "#food,log",
              "tex": { "texFile": "xie_misc.png", "all": { "0": 16, "4": 20 } }
            }
          },
          "recipes": {
            "campfire": { "pattern": "XX,XX", "content": "stick" }
          }
        }
        """;

    @Test
    void readsNestedBlockTextures() {
        XieFileReader.Result result = read(CAMPFIRE);
        assertTrue(diagnostics.errors().isEmpty(), diagnostics::toString);

        BlockDef campfire = result.blocks().get("campfire");
        assertEquals("furnace", campfire.archetype());
        assertTrue(campfire.props().getBoolean("naked", false));
        assertEquals(400, campfire.props().getInt("cookTime", 0));

        assertEquals("xie_misc.png", campfire.tex().sheet().orElseThrow());
        assertEquals(16, campfire.tex().spriteFor(TexDef.FACE_ALL, 0).orElseThrow());
        assertEquals(20, campfire.tex().spriteFor(TexDef.FACE_ALL, 4).orElseThrow());
        assertEquals(java.util.Set.of(16, 20), campfire.tex().referencedSprites());
    }

    /**
     * A texture declaration is indices and, separately, a sheet to read them from.
     * {@code blazeTree} gives the first and not the second, which is not a broken
     * declaration: 1.6's {@code SpritesheetManager} answered a definition that named
     * no sheet with {@code /terrain.png}, so the indices address vanilla's own. The
     * reader's job is to keep them apart and hand both on — see
     * {@link mod.xie.xm.content.model.LegacyTerrain}, which is where 31 becomes fire.
     */
    @Test
    void readsSpriteIndicesWithNoSheetToReadThemFrom() {
        XieFileReader.Result result = read("""
            { "blocks": { "blazeTree": {
                "name": "Blaze Leaves", "type": "tree", "trunk": "blazeLog",
                "renderType": "flower", "lightValue": 1.0,
                "tex": { "all": { "0": 31 } } } } }
            """);
        assertTrue(diagnostics.errors().isEmpty(), diagnostics::toString);

        BlockDef blazeTree = result.blocks().get("blazeTree");
        assertTrue(blazeTree.tex().sheet().isEmpty());
        assertTrue(blazeTree.tex().hasSprites());
        assertEquals(31, blazeTree.tex().spriteFor(TexDef.FACE_ALL, 0).orElseThrow());
    }

    @Test
    void readsShapedRecipeAndBindsSymbolsPositionally() {
        XieFileReader.Result result = read(CAMPFIRE);
        RecipeDef recipe = result.recipes().get(0);

        assertEquals(RecipeDef.Kind.SHAPED, recipe.kind());
        assertEquals(List.of("XX", "XX"), recipe.pattern());
        Map<Character, ItemRef> symbols = recipe.symbolMap();
        assertEquals(1, symbols.size());
        assertEquals("stick", symbols.get('X').name());
        assertTrue(recipe.problems().isEmpty());
    }

    @Test
    void bindsSeveralSymbolsInOrderOfFirstAppearance() {
        XieFileReader.Result result = read("""
            { "recipes": { "thing": { "pattern": "XY,YX", "content": "a,b" } } }
            """);
        Map<Character, ItemRef> symbols = result.recipes().get(0).symbolMap();
        assertEquals("a", symbols.get('X').name());
        assertEquals("b", symbols.get('Y').name());
    }

    // -- aliases and culls ----------------------------------------------------

    @Test
    void readsAliasesAndAliasedRecipes() {
        XieFileReader.Result result = read("""
            {
              "aliases": { "salady": "lettuce,tomato,onion" },
              "recipes": {
                "sammichS": { "product": "snackSammich:2", "content": "bread,bread,$salady" }
              }
            }
            """);
        assertEquals(1, result.aliases().size());
        assertEquals(List.of("lettuce", "tomato", "onion"),
            result.aliases().get(0).members().stream().map(ItemRef::name).toList());

        RecipeDef recipe = result.recipes().get(0);
        assertEquals("snackSammich", recipe.product().orElseThrow().name());
        assertEquals(2, recipe.product().orElseThrow().count());
        assertTrue(recipe.usesAliases());
        assertEquals(ItemRef.RefKind.ALIAS, recipe.ingredients().get(2).kind());
    }

    @Test
    void readsOverrideAndCull() {
        XieFileReader.Result result = read("""
            {
              "blocks": {
                "melon": { "override": "tile.melon", "name": "Melon", "drops": "tile.melon" }
              },
              "recipes": { "cull": "tile.melon", "item.melon:4": "tile.melon" }
            }
            """);
        BlockDef melon = result.blocks().get("melon");
        assertTrue(melon.isOverride());
        assertEquals("tile.melon", melon.override().orElseThrow().name());

        RecipeDef cull = result.recipes().stream()
            .filter(r -> r.kind() == RecipeDef.Kind.CULL).findFirst().orElseThrow();
        assertEquals(List.of("tile.melon"), cull.culls().stream().map(ItemRef::name).toList());

        RecipeDef replacement = result.recipes().stream()
            .filter(r -> r.kind() != RecipeDef.Kind.CULL).findFirst().orElseThrow();
        assertEquals(4, replacement.product().orElseThrow().count());
    }

    /** The original accepted this misspelling, and shipped content relies on it. */
    @Test
    void acceptsMisspelledOverride() {
        XieFileReader.Result result = read("""
            { "items": { "melon": { "overide": "item.melon", "name": "Melon" } } }
            """);
        assertTrue(result.items().get("melon").isOverride());
    }

    // -- file-level and error handling ---------------------------------------

    @Test
    void readsLegacyIdsAndTexturePaths() {
        XieFileReader.Result result = read("""
            {
              "ids": { "tomatoBush": 213 },
              "texturePaths": { "xie_crops.png": "textures/blocks/xie_crops.png" }
            }
            """);
        assertEquals(213, result.ids().get("tomatoBush"));
        assertEquals("textures/blocks/xie_crops.png", result.texturePaths().get("xie_crops.png"));
    }

    @Test
    void skipsUnderscorePrefixedSectionsAndDefinitions() {
        XieFileReader.Result result = read("""
            {
              "_blocks": { "disabled": { "name": "Nope" } },
              "items": { "_wip": { "name": "Nope" }, "real": { "name": "Yes" } }
            }
            """);
        assertTrue(result.blocks().isEmpty());
        assertEquals(1, result.items().size());
        assertTrue(result.items().containsKey("real"));
    }

    @Test
    void reportsInvalidJsonAsAnErrorRatherThanThrowing() {
        XieFileReader.Result result = read("{ this is not json");
        assertTrue(diagnostics.hasErrors());
        assertEquals(0, result.blocks().size());
    }

    @Test
    void reportsDuplicateKeysInsteadOfKeepingTheLastValue() {
        XieFileReader.Result result = read("""
            {
              "blocks": {
                "corn": {
                  "name": "Corn", "type": "crop",
                  "blockBounds": {
                    "3": "0,0,0,1,0.4375,1",
                    "4": "0,0,0,1,0.625,1",
                    "4": "0,0,0,1,0.9375,1"
                  }
                }
              }
            }
            """);
        assertTrue(diagnostics.hasErrors());
        assertEquals(0, result.blocks().size());
        Diagnostic error = diagnostics.errors().get(0);
        assertEquals("test.json", error.where());
        assertTrue(error.message().contains("duplicate key \"4\""), error.message());
        assertTrue(error.message().contains("blockBounds"), error.message());
    }

    @Test
    void acceptsTheSameKeyInDifferentObjects() {
        XieFileReader.Result result = read("""
            {
              "blocks": {
                "a": { "name": "A", "type": "crop", "blockBounds": { "4": "0,0,0,1,1,1" } },
                "b": { "name": "B", "type": "crop", "blockBounds": { "4": "0,0,0,1,1,1" } }
              }
            }
            """);
        assertFalse(diagnostics.hasErrors(), diagnostics.toString());
        assertEquals(2, result.blocks().size());
    }

    @Test
    void warnsOnUnknownSectionAndUnknownArchetype() {
        XieFileReader.Result result = read("""
            {
              "widgets": { "a": {} },
              "blocks": { "b": { "type": "hovercraft" } }
            }
            """);
        assertFalse(diagnostics.warnings().isEmpty());
        assertEquals(1, result.blocks().size(), "an unknown type still parses");
        assertFalse(result.blocks().get("b").hasKnownArchetype());
    }

    /**
     * {@code JobItem.doWork} dispatched {@code "seed"} and {@code "seeds"} to the
     * same {@code doSeeds}, so a definition written in the singular is a seed and
     * not an unknown archetype.
     */
    @Test
    void acceptsBothSpellingsOfSeed() {
        XieFileReader.Result result = read("""
            {
              "items": {
                "one":  { "name": "One",  "type": "seed",  "growsInto": "aBush" },
                "many": { "name": "Many", "type": "seeds", "growsInto": "aBush" }
              }
            }
            """);
        assertTrue(result.items().get("one").hasKnownArchetype());
        assertTrue(result.items().get("many").hasKnownArchetype());
        assertTrue(diagnostics.warnings().isEmpty(), diagnostics::toString);
    }

    /**
     * A {@code stick}'s {@code blocks} map, which is a drop table per block written
     * exactly as a block's own {@code drops} — and read in declaration order,
     * because the first entry that matches is the one that fires.
     */
    @Test
    void readsTheBlocksAStickWorksOn() {
        XieFileReader.Result result = read("""
            {
              "items": {
                "sickle": {
                  "name": "Sickle", "type": "stick", "uses": 64, "destroyChance": 100,
                  "blocks": {
                    "netherStalk:3": {
                      "etherWart":  { "chance": 2,  "amount": 1 },
                      "netherWart": { "chance": 20, "amount": 6 }
                    },
                    "pumpkin": "pumpkinSeeds"
                  }
                }
              }
            }
            """);
        assertTrue(diagnostics.errors().isEmpty(), diagnostics::toString);

        ItemDef sickle = result.items().get("sickle");
        assertEquals(2, sickle.blockDrops().size());

        ItemDef.BlockDrops wart = sickle.blockDrops().get(0);
        assertEquals("netherStalk", wart.block().name());
        // The suffix is a metadata value here — Xie.parseBlockAndMetadata read the
        // key, not Xie.parseItemStack — but ItemRef has one grammar and reads a
        // two-part reference as a count. The registrar drops the suffix either way,
        // so what matters is only that something was written after the colon.
        assertEquals(3, wart.block().count());
        assertEquals(List.of("etherWart", "netherWart"),
            wart.drops().drops().stream().map(d -> d.item().name()).toList());
        assertEquals(6, wart.drops().drops().get(1).amount());
        assertEquals(20, wart.drops().drops().get(1).chanceFor(0));

        // The shorthand string form works here too, since it is the same parse.
        assertEquals("pumpkin", sickle.blockDrops().get(1).block().name());
        assertTrue(sickle.blockDrops().get(1).drops().drops().get(0).isUnconditional());

        // "blocks" is structural, so it is not left over for the registrar to read.
        assertFalse(sickle.props().untouchedKeys().contains("blocks"));
    }

    @Test
    void tracksPropertiesNobodyRead() {
        XieFileReader.Result result = read("""
            { "blocks": { "b": { "name": "B", "type": "basic", "hardness": 1.0 } } }
            """);
        // name/type are consumed structurally; hardness is left for the registrar.
        assertTrue(result.blocks().get("b").props().untouchedKeys().contains("hardness"));
    }

    /**
     * {@code textureBase} is read by {@code readTex}, over a PropertyMap of its own
     * when the declaration is flat rather than nested in {@code tex} — so the
     * definition's map never sees the read, and the reader has to say what became of
     * it. What it says now is "parsed, waiting for a consumer": the consumer is the
     * cube-model writer, and it asks through {@link BlockDef#textureBase()}.
     */
    @Test
    void countsAFlatTextureBaseAsAStructureAwaitingItsConsumer() {
        XieFileReader.Result result = read("""
            { "blocks": { "saltDeposit": {
                "name": "Salt Deposit", "material": "sand", "textureBase": "sand" } } }
            """);
        BlockDef salt = result.blocks().get("saltDeposit");
        assertTrue(salt.props().unconsumedStructures().contains("texturebase"));
        assertTrue(salt.props().untouchedKeys().contains("texturebase"));

        assertEquals("sand", salt.textureBase().orElseThrow());
        assertFalse(salt.props().untouchedKeys().contains("texturebase"));
    }

    // -- structures that parse and are then consumed by nobody -----------------

    /**
     * The blind spot the report had until it was taught this: a structural key is read
     * by {@code XieFileReader} for every definition, so marking it consumed there said
     * only that the parser understood it. {@code blazeTree} declared a
     * {@code blockBounds} that parsed perfectly and that no archetype asked for, and
     * this is the shape of that.
     */
    @Test
    void leavesAParsedStructureUnreadUntilSomethingConsultsIt() {
        XieFileReader.Result result = read(TOMATO);
        BlockDef bush = result.blocks().get("tomatoBush");

        assertEquals(java.util.Set.of("drops", "blockbounds"), bush.props().unconsumedStructures());
        assertTrue(bush.props().untouchedKeys().containsAll(List.of("drops", "blockbounds")));

        bush.bounds();
        assertEquals(java.util.Set.of("drops"), bush.props().unconsumedStructures());
        bush.drops();
        assertTrue(bush.props().unconsumedStructures().isEmpty());
    }

    /**
     * {@code sheds} is a {@code tree}'s alone — {@code JobBlock} reads it in
     * {@code doTree} and nowhere else — so a definition declaring it on anything else
     * loses it, and the report is the only thing that will ever say so.
     */
    @Test
    void reportsAShedsNoArchetypeWillRead() {
        XieFileReader.Result result = read("""
            { "blocks": { "b": { "name": "B", "type": "basic",
                "sheds": { "apple": { "chance": 20 } } } } }
            """);
        BlockDef block = result.blocks().get("b");
        assertEquals(java.util.Set.of("sheds"), block.props().unconsumedStructures());
    }

    /**
     * A {@code blocks} map is a {@code stick}'s, exactly as it was in
     * {@code JobItem.doStick}. On anything else the entries parse and are handed to
     * nobody.
     */
    @Test
    void reportsABlocksMapNoArchetypeWillRead() {
        XieFileReader.Result result = read("""
            { "items": { "i": { "name": "I", "type": "food",
                "blocks": { "netherStalk": { "etherWart": { "chance": 2 } } } } } }
            """);
        ItemDef item = result.items().get("i");
        assertEquals(java.util.Set.of("blocks"), item.props().unconsumedStructures());

        assertEquals(1, item.blockDrops().size());
        assertTrue(item.props().unconsumedStructures().isEmpty());
    }

    /**
     * A key that parsed into nothing is settled by the reader, which has already
     * reported whatever was wrong with it. {@code zombiePigmelonStem} writes
     * {@code "tex": "#melonstem"} — a string where the format wants an object — and
     * that is one diagnostic, not a diagnostic and a report line as well.
     */
    @Test
    void doesNotDeferAStructureThatParsedIntoNothing() {
        XieFileReader.Result result = read("""
            { "blocks": { "zombiePigmelonStem": {
                "name": "Zombie Pigmelon Stem", "type": "stem",
                "grows": "zombiePigmelon", "tex": "#melonstem" } } }
            """);
        BlockDef stem = result.blocks().get("zombiePigmelonStem");
        assertEquals(1, diagnostics.warnings().size());
        assertTrue(stem.tex().isEmpty());
        assertTrue(stem.props().unconsumedStructures().isEmpty());
        assertFalse(stem.props().untouchedKeys().contains("tex"));
    }

    /**
     * A definition's own texture keys are one structure however they are written:
     * flat, as every item writes them, or nested in {@code tex}, as a block does.
     */
    @Test
    void treatsFlatAndNestedTextureKeysAsOneStructure() {
        XieFileReader.Result result = read(TOMATO);
        ItemDef tomato = result.items().get("tomato");

        assertEquals(java.util.Set.of("texfile", "icon"), tomato.props().unconsumedStructures());
        tomato.tex();
        assertTrue(tomato.props().unconsumedStructures().isEmpty());
    }

    // -- _wip/steel/steel.json -------------------------------------------------

    /**
     * A declaration the reader refuses is in none of the lists it returns, so it
     * would otherwise be missing from every count the registrar and the emitters
     * keep. {@code steel.json} writes {@code contents} where {@code JobRecipe} read
     * {@code content}, which failed the job outright in 1.6 and fails here — but it
     * failed three of six recipes while the pack reported one recipe not emitted.
     */
    @Test
    void countsRejectedDeclarations() {
        XieFileReader.Result result = read("""
            {
              "recipes": {
                "steelOre": { "pattern": " X ,XOX, X ", "contents": "coal,ironOre" },
                "purpleSteelOre": "obsidian,ironOre"
              }
            }
            """);
        assertEquals(1, result.recipes().size());
        assertEquals("purpleSteelOre", result.recipes().get(0).name());
        assertEquals(1, result.rejected());
        assertEquals(1, diagnostics.errors().size());
    }

    @Test
    void countsNothingRejectedWhenEverythingParses() {
        XieFileReader.Result result = read(TOMATO);
        assertEquals(0, result.rejected());
    }
}
