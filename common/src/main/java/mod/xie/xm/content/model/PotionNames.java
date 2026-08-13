package mod.xie.xm.content.model;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps the effect words a {@code potionEffect} property names onto modern effect ids.
 *
 * <p>1.6 had no effect registry to name things in. {@code Xie.parsePotionID} built
 * the string {@code "potion." + word} and compared it against every
 * {@code Potion.getName()}, so the word had to be the <em>field name</em> Mojang
 * happened to use — {@code moveSpeed}, {@code digSpeed}, {@code confusion} — and the
 * comparison was case-sensitive. Only one friendly synonym existed:
 * {@code Xie.potionNameTranslator} rewrote {@code speed} to {@code moveSpeed}, and
 * nothing else.
 *
 * <p>Most of those field names survive as modern ids once camelCase is split, so the
 * table below holds only the nine that genuinely moved. Anything not in it is passed
 * through {@link ResourceNames#toPath}, which turns {@code fireResistance} into
 * {@code fire_resistance} and leaves {@code poison} alone — and, incidentally, makes
 * the modern spellings work too, so a pack written today can say
 * {@code mining_fatigue}.
 *
 * <p>Whether the word names anything is left to the registry. This table only says
 * what a word <em>means</em>; a typo becomes an id that resolves to nothing, which is
 * where it should be reported.
 */
public final class PotionNames {

    /**
     * The 1.6 field names that are not simply the modern id in camelCase.
     *
     * <p>{@code digSlowDown} is spelt with the capital D its {@code setPotionName}
     * call used rather than the one its field used, because the comparison was
     * against the name; both spellings are accepted here, since the lookup is
     * case-insensitive and neither is ambiguous.
     */
    private static final Map<String, String> RENAMED = Map.of(
        "movespeed", "speed",
        "moveslowdown", "slowness",
        "digspeed", "haste",
        "digslowdown", "mining_fatigue",
        "damageboost", "strength",
        "heal", "instant_health",
        "harm", "instant_damage",
        "jump", "jump_boost",
        "confusion", "nausea"
    );

    private PotionNames() { }

    /**
     * The modern effect id one declared word names, e.g. {@code minecraft:speed}, or
     * empty when the word cannot be an id path at all.
     *
     * <p>A word already carrying a namespace is returned as written, so a pack can
     * name another mod's effect.
     */
    public static Optional<String> modern(String word) {
        if (word == null) {
            return Optional.empty();
        }
        String text = word.trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (text.contains(":")) {
            return Optional.of(text.toLowerCase(Locale.ROOT));
        }
        // The engine's own comparison string, in case content copied it wholesale.
        if (text.toLowerCase(Locale.ROOT).startsWith("potion.")) {
            text = text.substring("potion.".length());
        }

        String renamed = RENAMED.get(text.toLowerCase(Locale.ROOT));
        String path = renamed != null ? renamed : ResourceNames.toPath(text);
        return path.matches("[a-z0-9_.-]+")
            ? Optional.of("minecraft:" + path)
            : Optional.empty();
    }
}
