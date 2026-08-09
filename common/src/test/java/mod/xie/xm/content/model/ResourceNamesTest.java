package mod.xie.xm.content.model;

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
}
