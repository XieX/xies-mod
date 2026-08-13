package mod.xie.xm.content.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PotionNamesTest {

    /**
     * The nine that moved. {@code Xie.parsePotionID} matched {@code Potion.getName()},
     * so these are the strings 1.6 content had to write to reach them at all.
     */
    @Test
    void mapsTheEngineFieldNamesOntoModernIds() {
        assertEquals(Optional.of("minecraft:speed"), PotionNames.modern("moveSpeed"));
        assertEquals(Optional.of("minecraft:slowness"), PotionNames.modern("moveSlowdown"));
        assertEquals(Optional.of("minecraft:haste"), PotionNames.modern("digSpeed"));
        assertEquals(Optional.of("minecraft:mining_fatigue"), PotionNames.modern("digSlowDown"));
        assertEquals(Optional.of("minecraft:strength"), PotionNames.modern("damageBoost"));
        assertEquals(Optional.of("minecraft:instant_health"), PotionNames.modern("heal"));
        assertEquals(Optional.of("minecraft:instant_damage"), PotionNames.modern("harm"));
        assertEquals(Optional.of("minecraft:jump_boost"), PotionNames.modern("jump"));
        assertEquals(Optional.of("minecraft:nausea"), PotionNames.modern("confusion"));
    }

    /** The rest are the modern id in camelCase, so splitting the case is the whole job. */
    @Test
    void splitsCamelCaseForTheOnesThatDidNotMove() {
        assertEquals(Optional.of("minecraft:fire_resistance"), PotionNames.modern("fireResistance"));
        assertEquals(Optional.of("minecraft:water_breathing"), PotionNames.modern("waterBreathing"));
        assertEquals(Optional.of("minecraft:night_vision"), PotionNames.modern("nightVision"));
        assertEquals(Optional.of("minecraft:health_boost"), PotionNames.modern("healthBoost"));
        assertEquals(Optional.of("minecraft:poison"), PotionNames.modern("poison"));
        assertEquals(Optional.of("minecraft:regeneration"), PotionNames.modern("regeneration"));
    }

    /**
     * {@code speed} was the single synonym {@code Xie.potionNameTranslator} knew, and
     * {@code haste} was not — {@code chocolateMilk} asks for {@code haste} and got
     * nothing in 2013, because no {@code Potion} was named {@code potion.haste}. The
     * modern ids are those words, so both now mean what the content meant.
     */
    @Test
    void acceptsTheWordsTheContentActuallyWrites() {
        assertEquals(Optional.of("minecraft:speed"), PotionNames.modern("speed"));
        assertEquals(Optional.of("minecraft:haste"), PotionNames.modern("haste"));
        assertEquals(Optional.of("minecraft:mining_fatigue"), PotionNames.modern("mining_fatigue"));
    }

    @Test
    void toleratesCaseSpacingAndTheEnginesOwnPrefix() {
        assertEquals(Optional.of("minecraft:haste"), PotionNames.modern(" DIGSPEED "));
        assertEquals(Optional.of("minecraft:speed"), PotionNames.modern("potion.moveSpeed"));
    }

    /** A namespaced word is another mod's effect and is left exactly as written. */
    @Test
    void passesNamespacedIdsThrough() {
        assertEquals(Optional.of("somemod:caffeinated"), PotionNames.modern("somemod:caffeinated"));
    }

    /**
     * An unknown word still becomes an id. Whether anything answers to it is the
     * registry's question, not this table's, and that is where it gets reported.
     */
    @Test
    void leavesUnknownWordsToTheRegistry() {
        assertEquals(Optional.of("minecraft:spede"), PotionNames.modern("spede"));
        assertEquals(Optional.empty(), PotionNames.modern("  "));
        assertEquals(Optional.empty(), PotionNames.modern(null));
    }
}
