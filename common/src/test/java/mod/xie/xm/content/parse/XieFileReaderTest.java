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
        return new XieFileReader(diagnostics).read("test.xie", "test", new StringReader(json));
    }

    // -- tomato.xie -----------------------------------------------------------

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

    // -- campfire.xie ---------------------------------------------------------

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

    @Test
    void tracksPropertiesNobodyRead() {
        XieFileReader.Result result = read("""
            { "blocks": { "b": { "name": "B", "type": "basic", "hardness": 1.0 } } }
            """);
        // name/type are consumed structurally; hardness is left for the registrar.
        assertTrue(result.blocks().get("b").props().untouchedKeys().contains("hardness"));
    }
}
