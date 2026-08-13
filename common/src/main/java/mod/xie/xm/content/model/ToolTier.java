package mod.xie.xm.content.model;

import java.util.Locale;
import java.util.Optional;

/**
 * The tool tier a {@code material} property names.
 *
 * <p>{@code JobItem.getToolMaterial} handed the string to
 * {@code XieMaterialManager.getToolMaterial}, which upper-cased it and looked for a
 * matching {@code EnumToolMaterial} constant. So the vocabulary was Mojang's field
 * names rather than anything the format defined, and an unrecognised word logged a
 * line and silently became wood.
 *
 * <p>Which is the trap: 1.6 called the diamond tier {@code EMERALD}, so
 * {@code "material": "diamond"} matched nothing and {@code ultimateFist} was a
 * <em>wooden</em> tool in 2013 — 59 uses on an item that costs seven diamonds. Both
 * spellings resolve to the diamond tier here, on the same grounds
 * {@link PotionNames} maps both spellings of an effect: the modern name is the word
 * the content already used, so the declaration now means what it says. The tiers
 * Mojang has added since are recognised too, for content that has not been written
 * yet.
 *
 * <p>Only the tier is decided here. What a tier is worth — durability, mining
 * speed, damage bonus, enchantability — is vanilla's {@code ToolMaterial} and is
 * read from it rather than copied.
 */
public enum ToolTier {
    WOOD,
    STONE,
    COPPER,
    IRON,
    DIAMOND,
    GOLD,
    NETHERITE;

    /** What an absent or unrecognised {@code material} meant, and still means. */
    public static final ToolTier DEFAULT = WOOD;

    /**
     * The tier a {@code material} string names, or empty when nothing does.
     *
     * <p>Callers that want the legacy fallback should report the miss and then use
     * {@link #DEFAULT}, which is what the original did.
     */
    public static Optional<ToolTier> forName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = name.trim().toUpperCase(Locale.ROOT);
        if (wanted.equals("EMERALD")) {
            // 1.6's own name for the diamond tier, and the reason "diamond" missed.
            return Optional.of(DIAMOND);
        }
        if (wanted.equals("WOODEN")) {
            return Optional.of(WOOD);
        }
        if (wanted.equals("GOLDEN")) {
            return Optional.of(GOLD);
        }
        for (ToolTier tier : values()) {
            if (tier.name().equals(wanted)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }
}
