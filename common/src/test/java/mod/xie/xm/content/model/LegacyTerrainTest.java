package mod.xie.xm.content.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTerrainTest {

    @Test
    void resolvesTheSpriteTheFlameTreeDraws() {
        // flame_tree.json says "tex": {"all": {"0": 31}} and no texFile, which is
        // Block.fire's index into vanilla's terrain.png and the whole reason this
        // table exists.
        assertEquals(Optional.of("fire_0"), LegacyTerrain.texture(31));
    }

    @Test
    void hasNothingForAnIndexNobodyAddresses() {
        // An index this table cannot answer for is reported by name and number at emit
        // time rather than guessed at: see XiePackEmitter.borrowedSprite.
        assertTrue(LegacyTerrain.texture(0).isEmpty());
        assertTrue(LegacyTerrain.texture(47).isEmpty());
        assertTrue(LegacyTerrain.texture(255).isEmpty());
    }

    @Test
    void answersForNoIndexOffTheSheet() {
        assertTrue(LegacyTerrain.texture(-1).isEmpty());
        assertTrue(LegacyTerrain.texture(256).isEmpty());
    }

    @Test
    void doesNotShareTheBlockIdNumbering() {
        // 31 is fire here and short grass there. A lookup that consulted either table
        // for the other's question would be wrong without saying so.
        assertEquals(Optional.of("short_grass"), LegacyBlockIds.path(31));
    }
}
