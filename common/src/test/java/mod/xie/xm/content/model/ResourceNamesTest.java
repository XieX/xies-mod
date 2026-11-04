package mod.xie.xm.content.model;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceNamesTest {

    @Test
    void splitsCamelCase() {
        assertEquals("tomato_bush", ResourceNames.toPath("tomatoBush"));
        assertEquals("tomato_seeds", ResourceNames.toPath("tomatoSeeds"));
    }

    @Test
    void splitsAcronymsWhereAReaderWould() {
        assertEquals("oj", ResourceNames.toPath("OJ"));
        assertEquals("sammich_ssp", ResourceNames.toPath("sammichSSP"));
    }

    @Test
    void replacesCharactersAnIdCannotHold() {
        // Recipe keys carry a stack count, "cornKernels:2", and become part of an id.
        assertEquals("corn_kernels_2", ResourceNames.toPath("cornKernels:2"));
        assertEquals("unnamed", ResourceNames.toPath(""));
    }

    @Test
    void foldsAccentsAndDropsOtherLettersAnIdCannotHold() {
        assertEquals("creme_brulee", ResourceNames.toPath("Crème Brûlée"));
        assertEquals("tea", ResourceNames.toPath("tea茶"));
    }

    @Test
    void renamesVanillaNamesMojangHasChanged() {
        assertEquals("crafting_table", ResourceNames.toVanillaPath(ItemRef.parse("tile.workbench")));
        assertEquals("cooked_porkchop", ResourceNames.toVanillaPath(ItemRef.parse("porkchopCooked")));
        assertEquals("short_grass", ResourceNames.toVanillaPath(ItemRef.parse("tile.tallgrass")));
    }

    @Test
    void passesUnmappedVanillaNamesThrough() {
        assertEquals("bread", ResourceNames.toVanillaPath(ItemRef.parse("bread")));
        assertEquals("bowl", ResourceNames.toVanillaPath(ItemRef.parse("bowl")));
    }

    /**
     * The 1.6 prefixes are the only thing separating a melon block from a slice, so
     * dropping them collapses "one melon block crafts four slices" into a recipe
     * that turns one melon into four melons.
     */
    @Test
    void distinguishesBlockAndItemOfTheSameName() {
        assertEquals("melon", ResourceNames.toVanillaPath(ItemRef.parse("tile.melon")));
        assertEquals("melon_slice", ResourceNames.toVanillaPath(ItemRef.parse("item.melon:4")));
    }

    /** A bare "melon" has no prefix to consult, so it stays the block. */
    @Test
    void treatsAnUnprefixedNameAsTheBlock() {
        assertEquals("melon", ResourceNames.toVanillaPath(ItemRef.parse("melon")));
    }

    /**
     * 1.6 had two flowers named for their colours, and the bare name meant the
     * yellow one. Getting these wrong silently drops the flower bush recipes.
     */
    @Test
    void mapsTheTwoLegacyFlowers() {
        assertEquals("poppy", ResourceNames.toVanillaPath(ItemRef.parse("rose")));
        assertEquals("dandelion", ResourceNames.toVanillaPath(ItemRef.parse("flower")));
        assertEquals("dandelion", ResourceNames.toVanillaPath(ItemRef.parse("yellowFlower")));
    }

    /**
     * The nether names, which only the nether packs use. Soul sand is what a
     * {@code plantType: nether} grows on, so a miss here leaves a crop that can be
     * planted nowhere at all — and says nothing about it.
     */
    @Test
    void mapsTheNetherNames() {
        assertEquals("soul_sand", ResourceNames.toVanillaPath(ItemRef.parse("soulsand")));
        assertEquals("soul_sand", ResourceNames.toVanillaPath(ItemRef.parse("slowSand")));
        assertEquals("nether_wart", ResourceNames.toVanillaPath(ItemRef.parse("netherStalk")));
        assertEquals("nether_wart", ResourceNames.toVanillaPath(ItemRef.parse("netherWart")));
        assertEquals("nether_wart", ResourceNames.toVanillaPath(ItemRef.parse("netherStalkSeeds")));
    }

    @Test
    void mapsRawFoodsAndBuckets() {
        assertEquals("beef", ResourceNames.toVanillaPath(ItemRef.parse("beefRaw")));
        assertEquals("porkchop", ResourceNames.toVanillaPath(ItemRef.parse("porkchopRaw")));
        assertEquals("water_bucket", ResourceNames.toVanillaPath(ItemRef.parse("bucketWater")));
        assertEquals("milk_bucket", ResourceNames.toVanillaPath(ItemRef.parse("milk")));
        assertEquals("milk_bucket", ResourceNames.toVanillaPath(ItemRef.parse("milkBucket")));
    }

    /**
     * Foods added after 1.6 have no legacy name, so content writes the modern id in
     * camelCase. An alias member that misses is left out of its tag without a word,
     * so a wrong mapping here quietly drops rabbit from {@code $protein}.
     */
    @Test
    void passesFoodsNewerThanTheFormatThrough() {
        assertEquals("cooked_rabbit", ResourceNames.toVanillaPath(ItemRef.parse("cookedRabbit")));
        assertEquals("cooked_mutton", ResourceNames.toVanillaPath(ItemRef.parse("cookedMutton")));
        assertEquals("cooked_salmon", ResourceNames.toVanillaPath(ItemRef.parse("cookedSalmon")));
        assertEquals("sweet_berries", ResourceNames.toVanillaPath(ItemRef.parse("sweetBerries")));
        assertEquals("glow_berries", ResourceNames.toVanillaPath(ItemRef.parse("glowBerries")));
        assertEquals("beetroot", ResourceNames.toVanillaPath(ItemRef.parse("beetroot")));
        assertEquals("mutton", ResourceNames.toVanillaPath(ItemRef.parse("mutton")));
        assertEquals("rabbit", ResourceNames.toVanillaPath(ItemRef.parse("rabbit")));
        assertEquals("salmon", ResourceNames.toVanillaPath(ItemRef.parse("salmon")));
        assertEquals("dried_kelp", ResourceNames.toVanillaPath(ItemRef.parse("driedKelp")));
        assertEquals("baked_potato", ResourceNames.toVanillaPath(ItemRef.parse("bakedPotato")));
        assertEquals("pumpkin_pie", ResourceNames.toVanillaPath(ItemRef.parse("pumpkinPie")));
    }

    /**
     * {@code dyePowder} was one id with sixteen damage values, so the subtype is
     * the only thing that says which of sixteen modern items is meant. Content uses
     * {@code dyePowder:1:3} — a count of one, metadata three — for cocoa beans.
     */
    @Test
    void resolvesDyeSubtypesByMetadata() {
        assertEquals("cocoa_beans", ResourceNames.toVanillaPath(ItemRef.parse("dyePowder:1:3")));
        assertEquals("red_dye", ResourceNames.toVanillaPath(ItemRef.parse("dyePowder:1:1")));
        assertEquals("bone_meal", ResourceNames.toVanillaPath(ItemRef.parse("dyePowder:1:15")));
        assertEquals("ink_sac", ResourceNames.toVanillaPath(ItemRef.parse("dyePowder")));
    }

    /**
     * {@code potion:1:0} is a water bottle, and the modern potion item needs its
     * contents named to mean only that. Other damage values stay unmapped.
     */
    @Test
    void resolvesWaterBottleByMetadata() {
        assertEquals(Optional.of("minecraft:water"), ResourceNames.legacyPotion(ItemRef.parse("potion:1:0")));
        assertEquals(Optional.of("minecraft:water"), ResourceNames.legacyPotion(ItemRef.parse("potion")));
        assertEquals(Optional.empty(), ResourceNames.legacyPotion(ItemRef.parse("potion:1:16")));
        assertEquals(Optional.empty(), ResourceNames.legacyPotion(ItemRef.parse("glassBottle")));
    }

    /**
     * A dye written by its own name needs no subtype table: {@code redDye} is already
     * the modern id in camelCase. {@code flowerbushes.json} writes its culls that way,
     * with a count rather than a metadata value — {@code redDye:2} is two red dye —
     * so the count must not be mistaken for a subtype and change which item is meant.
     */
    @Test
    void resolvesDyesWrittenByName() {
        assertEquals("red_dye", ResourceNames.toVanillaPath(ItemRef.parse("redDye")));
        assertEquals("yellow_dye", ResourceNames.toVanillaPath(ItemRef.parse("yellowDye")));
        assertEquals("red_dye", ResourceNames.toVanillaPath(ItemRef.parse("redDye:2")));
        assertEquals("yellow_dye", ResourceNames.toVanillaPath(ItemRef.parse("yellowDye:2")));
    }

    @Test
    void mapsRemainingRenamedItems() {
        assertEquals("cocoa_beans", ResourceNames.toVanillaPath(ItemRef.parse("cocoabeans")));
        assertEquals("melon_seeds", ResourceNames.toVanillaPath(ItemRef.parse("seeds_melon")));
        assertEquals("sugar_cane", ResourceNames.toVanillaPath(ItemRef.parse("reed")));
        assertEquals("nether_wart", ResourceNames.toVanillaPath(ItemRef.parse("netherStalkSeeds")));
    }

    /**
     * The names a tool's {@code blocksEffective} and {@code canHarvest} lists are
     * written in — {@code survivalpickaxe.json} names eleven blocks and eight of them
     * have been renamed since. The ore family is the one that has to be spelt out:
     * every one of them was the noun first in 1.6 and is the noun last now, so
     * {@code oreIron} otherwise resolves to a plausible-looking {@code ore_iron}
     * that no block answers to.
     */
    @Test
    void mapsBlockNamesAToolListNames() {
        assertEquals("iron_ore", ResourceNames.toVanillaPath(ItemRef.parse("oreIron")));
        assertEquals("coal_ore", ResourceNames.toVanillaPath(ItemRef.parse("oreCoal")));
        assertEquals("stone_bricks", ResourceNames.toVanillaPath(ItemRef.parse("stonebrick")));
        assertEquals("mossy_cobblestone", ResourceNames.toVanillaPath(ItemRef.parse("stoneMoss")));
        assertEquals("sandstone", ResourceNames.toVanillaPath(ItemRef.parse("sandStone")));
        assertEquals("smooth_stone_slab", ResourceNames.toVanillaPath(ItemRef.parse("stoneSlab")));
        assertEquals("netherrack", ResourceNames.toVanillaPath(ItemRef.parse("hellrock")));
        // SurvivalTweaks' furnace recipe, the one 1.6 block name that was still unmapped.
        assertEquals("clay", ResourceNames.toVanillaPath(ItemRef.parse("clayBlock")));
        // A sword's canCut list, which defaults to exactly this one block.
        assertEquals("cobweb", ResourceNames.toVanillaPath(ItemRef.parse("web")));
        assertEquals("powered_rail", ResourceNames.toVanillaPath(ItemRef.parse("goldenRail")));

        // The three the table does not need to touch.
        assertEquals("ice", ResourceNames.toVanillaPath(ItemRef.parse("ice")));
        assertEquals("rail", ResourceNames.toVanillaPath(ItemRef.parse("rail")));
        assertEquals("detector_rail", ResourceNames.toVanillaPath(ItemRef.parse("detectorRail")));
    }

    /**
     * The names a vanilla override names. {@code survivalpickaxe.json} overrides
     * {@code pickaxeWood} and {@code pickaxeStone}, which are 1.6's own field names
     * and are the noun first, exactly as the ores are.
     */
    @Test
    void mapsToolNamesWrittenTheNineteenSixWayRound() {
        assertEquals("wooden_pickaxe", ResourceNames.toVanillaPath(ItemRef.parse("pickaxeWood")));
        assertEquals("stone_pickaxe", ResourceNames.toVanillaPath(ItemRef.parse("pickaxeStone")));

        // The rest of the family, which no content overrides yet but which is the
        // same mechanical rename.
        assertEquals("wooden_sword", ResourceNames.toVanillaPath(ItemRef.parse("swordWood")));
        assertEquals("diamond_axe", ResourceNames.toVanillaPath(ItemRef.parse("axeDiamond")));
        assertEquals("golden_shovel", ResourceNames.toVanillaPath(ItemRef.parse("shovelGold")));
        assertEquals("iron_hoe", ResourceNames.toVanillaPath(ItemRef.parse("hoeIron")));
        // 1.6 called the iron tier "Steel" in its field names.
        assertEquals("iron_pickaxe", ResourceNames.toVanillaPath(ItemRef.parse("pickaxeSteel")));
    }

    /**
     * The other way round, which needs no table at all: {@code cullStoneTools.json}
     * writes the tier first and the plain camelCase split already lands on the
     * modern id.
     */
    @Test
    void leavesToolNamesWrittenTierFirstAlone() {
        assertEquals("stone_pickaxe", ResourceNames.toVanillaPath(ItemRef.parse("stonePickaxe")));
        assertEquals("stone_sword", ResourceNames.toVanillaPath(ItemRef.parse("stoneSword")));
        assertEquals("stone_hoe", ResourceNames.toVanillaPath(ItemRef.parse("stoneHoe")));
    }

    /** A name that merely begins with a tool word is not a tool. */
    @Test
    void doesNotMistakeOtherNamesForTools() {
        assertEquals("hoe_handle", ResourceNames.toVanillaPath(ItemRef.parse("hoeHandle")));
        assertEquals("axe_head", ResourceNames.toVanillaPath(ItemRef.parse("axeHead")));
    }
}
