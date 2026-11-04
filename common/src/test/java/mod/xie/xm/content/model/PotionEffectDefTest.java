package mod.xie.xm.content.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionEffectDefTest {

    /** {@code tea.json} in full: Speed I for twenty seconds, one drink in five. */
    @Test
    void readsTheFullForm() {
        PotionEffectDef effect = PotionEffectDef.parse("speed:20:1@20").orElseThrow();

        assertEquals("minecraft:speed", effect.effect());
        assertEquals(400, effect.durationTicks());
        assertEquals(0, effect.amplifier());
        assertEquals(0.2f, effect.chance(), 1e-6);
        assertFalse(effect.isCertain());
    }

    /** Seconds, not ticks — {@code Xie.parsePotionEffect} multiplied by twenty. */
    @Test
    void durationIsInSeconds() {
        assertEquals(600, PotionEffectDef.parse("poison:30").orElseThrow().durationTicks());
    }

    /** Levels are one-based in the content and zero-based in the game. */
    @Test
    void levelIsOneBased() {
        assertEquals(0, PotionEffectDef.parse("speed:10:1").orElseThrow().amplifier());
        assertEquals(2, PotionEffectDef.parse("speed:10:3").orElseThrow().amplifier());
        assertEquals(0, PotionEffectDef.parse("speed:10:0").orElseThrow().amplifier());
    }

    @Test
    void bareNameGetsAMinuteAtLevelOneEveryTime() {
        PotionEffectDef effect = PotionEffectDef.parse("regeneration").orElseThrow();

        assertEquals("minecraft:regeneration", effect.effect());
        assertEquals(PotionEffectDef.DEFAULT_DURATION_TICKS, effect.durationTicks());
        assertEquals(0, effect.amplifier());
        assertTrue(effect.isCertain());
    }

    /** A percentage, which is why it is written {@code @20} rather than {@code @0.2}. */
    @Test
    void chanceIsAPercentage() {
        assertEquals(1f, PotionEffectDef.parse("poison@100").orElseThrow().chance(), 1e-6);
        assertEquals(0.05f, PotionEffectDef.parse("poison@5").orElseThrow().chance(), 1e-6);
    }

    @Test
    void commaSeparatesSeveral() {
        List<PotionEffectDef> effects = PotionEffectDef.parseAll("speed:20:1@20, nightVision:60");

        assertEquals(2, effects.size());
        assertEquals("minecraft:speed", effects.get(0).effect());
        assertEquals("minecraft:night_vision", effects.get(1).effect());
        assertEquals(1200, effects.get(1).durationTicks());
    }

    /** Another mod's effect, which needs a duration or "minecraft:" to say it is one. */
    @Test
    void readsANamespacedEffect() {
        PotionEffectDef effect = PotionEffectDef.parse("somemod:caffeinated:30:2").orElseThrow();
        assertEquals("somemod:caffeinated", effect.effect());
        assertEquals(600, effect.durationTicks());
        assertEquals(1, effect.amplifier());
        assertEquals("minecraft:speed", PotionEffectDef.parse("minecraft:speed").orElseThrow().effect());
    }

    /** A namespaced id the registry could not even parse is unreadable, not a crash. */
    @Test
    void refusesAMalformedNamespacedEffect() {
        assertEquals(Optional.empty(), PotionEffectDef.parse("somemod:my effect:30"));
        assertEquals(List.of("minecraft:sp@ed"), PotionEffectDef.unresolved("minecraft:sp@ed"));
    }

    /** A malformed entry is dropped rather than throwing, and is nameable afterwards. */
    @Test
    void reportsEntriesItCouldNotRead() {
        assertEquals(Optional.empty(), PotionEffectDef.parse("speed:soon"));
        assertEquals(List.of("speed:soon"), PotionEffectDef.unresolved("poison, speed:soon"));
        assertEquals(1, PotionEffectDef.parseAll("poison, speed:soon").size());
        assertEquals(List.of(), PotionEffectDef.parseAll(""));
    }
}
