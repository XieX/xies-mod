package mod.xie.xm.content.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolTierTest {

    @Test
    void readsTheNamesTheContentUses() {
        assertEquals(Optional.of(ToolTier.STONE), ToolTier.forName("stone"));
        assertEquals(Optional.of(ToolTier.WOOD), ToolTier.forName("wood"));
        assertEquals(Optional.of(ToolTier.IRON), ToolTier.forName("IRON"));
        assertEquals(Optional.of(ToolTier.GOLD), ToolTier.forName(" gold "));
    }

    /**
     * The trap. 1.6 called the diamond tier {@code EMERALD}, and
     * {@code XieMaterialManager} matched {@code EnumToolMaterial} constants by name,
     * so {@code ultimateFist}'s {@code "material": "diamond"} found nothing and got
     * wood. Both spellings answer now.
     */
    @Test
    void readsBothSpellingsOfTheDiamondTier() {
        assertEquals(Optional.of(ToolTier.DIAMOND), ToolTier.forName("diamond"));
        assertEquals(Optional.of(ToolTier.DIAMOND), ToolTier.forName("emerald"));
    }

    @Test
    void acceptsTheAdjectiveFormsVanillaItemsAreNamedWith() {
        assertEquals(Optional.of(ToolTier.WOOD), ToolTier.forName("wooden"));
        assertEquals(Optional.of(ToolTier.GOLD), ToolTier.forName("golden"));
    }

    @Test
    void answersForNothingItDoesNotKnow() {
        assertEquals(Optional.empty(), ToolTier.forName("obsidian"));
        assertEquals(Optional.empty(), ToolTier.forName(""));
        assertEquals(Optional.empty(), ToolTier.forName(null));
        assertEquals(ToolTier.WOOD, ToolTier.DEFAULT);
    }
}
