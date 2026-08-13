package mod.xie.xm.content.model;

import java.util.Locale;

/**
 * How a block is drawn, from the {@code renderType} property.
 *
 * <p>In 1.6 {@code renderType} was an integer selecting one of Minecraft's
 * hard-coded block renderers, and {@code Xie.parseRenderType} mapped the handful
 * of names content used onto those numbers: {@code flower} and {@code sapling}
 * were 1, {@code crop} was 6, everything else 0. There are no renderer ids any
 * more — a block model's {@code parent} decides its shape — so the same names now
 * pick a vanilla parent model instead.
 *
 * <p>Content usually leaves the property out and lets the archetype decide, which
 * is why {@link #forBlock} falls back to it. Where both are given they agree:
 * {@code lettucePlant} is a {@code plant} and says {@code "renderType": "flower"}.
 * Where they disagree the property wins, and that is the point of it — {@code shrub}
 * is a {@code basic} block, which would otherwise be a cube, drawn as a cross.
 */
public enum RenderStyle {

    /** A solid six-sided block; {@code minecraft:block/cube}. */
    CUBE,

    /** Two crossed quads, as flowers and saplings are drawn; {@code block/cross}. */
    CROSS,

    /** Four offset quads, as wheat is drawn; {@code block/crop}. */
    CROP;

    /** The style a {@code renderType} value names, or {@link #CUBE} if unrecognised. */
    public static RenderStyle parse(String renderType) {
        if (renderType == null) {
            return CUBE;
        }
        return switch (renderType.trim().toLowerCase(Locale.ROOT)) {
            case "flower", "billboard", "sapling", "cross" -> CROSS;
            case "crop", "crops" -> CROP;
            default -> CUBE;
        };
    }

    /** The style an archetype implies when a definition declares no {@code renderType}. */
    public static RenderStyle forArchetype(String archetype) {
        if (archetype == null) {
            return CUBE;
        }
        return switch (archetype.trim().toLowerCase(Locale.ROOT)) {
            case "bush", "plant", "sapling" -> CROSS;
            case "crop" -> CROP;
            default -> CUBE;
        };
    }

    /** The declared {@code renderType} if there is one, otherwise the archetype's. */
    public static RenderStyle forBlock(BlockDef def) {
        return def.props().getString("renderType")
            .map(RenderStyle::parse)
            .orElseGet(() -> forArchetype(def.archetype()));
    }

    /** Whether a block of this style occupies its whole cube. */
    public boolean isCube() {
        return this == CUBE;
    }
}
