package mod.xie.xm.content.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One effect a {@code potionEffect} property asks for.
 *
 * <p>The format is {@code name:seconds:level@percent}, all but the name optional,
 * and a comma separates several of them:
 *
 * <pre>
 *   "potionEffect": "speed:20:1@20"       -&gt; Speed I for 20s, one time in five
 *   "potionEffect": "regeneration"        -&gt; Regeneration I for a minute, always
 * </pre>
 *
 * <p>Three conversions come from {@code Xie.parsePotionEffect} and none are
 * obvious from the text: seconds are multiplied by twenty, the level is a
 * <em>one-based</em> number so the amplifier is one less, and the chance is a
 * percentage rather than a fraction. An entry with no duration gets a minute, which
 * was that method's {@code defaultDuration}.
 *
 * @param effect        the modern effect id, e.g. {@code minecraft:speed}
 * @param durationTicks how long it lasts
 * @param amplifier     zero-based, so Speed I is 0
 * @param chance        0..1; 1 means always
 */
public record PotionEffectDef(String effect, int durationTicks, int amplifier, float chance) {

    /** What an entry that names no duration gets, from {@code Xie.defaultDuration}. */
    public static final int DEFAULT_DURATION_TICKS = 1200;

    private static final int TICKS_PER_SECOND = 20;

    /**
     * Every effect a property value asks for, in declaration order. Entries naming
     * nothing modern are left out; {@link #unresolved} names them.
     */
    public static List<PotionEffectDef> parseAll(String spec) {
        List<PotionEffectDef> effects = new ArrayList<>();
        for (String entry : split(spec)) {
            parse(entry).ifPresent(effects::add);
        }
        return List.copyOf(effects);
    }

    /** The entries of a property value that named no modern effect, for reporting. */
    public static List<String> unresolved(String spec) {
        List<String> bad = new ArrayList<>();
        for (String entry : split(spec)) {
            if (parse(entry).isEmpty()) {
                bad.add(entry);
            }
        }
        return List.copyOf(bad);
    }

    /** One {@code name:seconds:level@percent} entry. */
    public static Optional<PotionEffectDef> parse(String entry) {
        if (entry == null || entry.isBlank()) {
            return Optional.empty();
        }

        String text = entry.trim();
        float chance = 1f;
        int at = text.indexOf('@');
        if (at >= 0) {
            Optional<Integer> percent = readInt(text.substring(at + 1));
            if (percent.isEmpty()) {
                return Optional.empty();
            }
            chance = Math.clamp(percent.get() / 100f, 0f, 1f);
            text = text.substring(0, at);
        }

        String[] parts = text.split(":");
        Optional<String> effect = PotionNames.modern(parts[0]);
        if (effect.isEmpty()) {
            return Optional.empty();
        }

        int duration = DEFAULT_DURATION_TICKS;
        if (parts.length > 1) {
            Optional<Integer> seconds = readInt(parts[1]);
            if (seconds.isEmpty()) {
                return Optional.empty();
            }
            duration = Math.max(1, seconds.get() * TICKS_PER_SECOND);
        }

        int amplifier = 0;
        if (parts.length > 2) {
            Optional<Integer> level = readInt(parts[2]);
            if (level.isEmpty()) {
                return Optional.empty();
            }
            // Levels are one-based in the content and zero-based in the game. A
            // level of zero would be an amplifier of -1, which is not a weaker
            // effect but a broken one, so the floor is level I.
            amplifier = Math.max(0, level.get() - 1);
        }

        return Optional.of(new PotionEffectDef(effect.get(), duration, amplifier, chance));
    }

    /** Whether this always applies, and so needs no probability written out. */
    public boolean isCertain() {
        return chance >= 1f;
    }

    private static List<String> split(String spec) {
        if (spec == null || spec.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String part : spec.split(",")) {
            if (!part.isBlank()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }

    private static Optional<Integer> readInt(String text) {
        try {
            return Optional.of(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
