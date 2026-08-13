package mod.xie.xm.content.model;

import java.util.Locale;

/**
 * The silhouette a {@code tree} grows into, from the {@code shape} property.
 *
 * <p>{@code TreeGenerator} had three generators behind four names —
 * {@code BIRCH} and the default both ran {@code generateOak} — and each of the
 * three was 1.6's own tree generator copied out of vanilla: {@code generateOak}
 * is {@code WorldGenTrees}, {@code generateConifer} is {@code WorldGenTaiga2} and
 * {@code generateTallConifer} is {@code WorldGenTaiga1}.
 *
 * <p>That lineage is the whole reason the port does not need {@code TreeGenerator}:
 * those three generators are still in the game, as the {@code minecraft:tree}
 * feature configured three ways, so a shape here picks a configured feature rather
 * than a method to call. See {@code XieWorldgenEmitter}.
 *
 * <p>Nothing in the live content declares {@code shape}, so every fruit tree is an
 * oak — which is what {@code XieBlockLeaves.generate} defaulted to when
 * {@code shape} was left null.
 */
public enum TreeShape {

    /** A short trunk under a blob of leaves; 1.6's oak, and the default. */
    OAK,

    /** A tapering spire; 1.6's spruce. */
    CONIFER,

    /** A taller spire with a bare lower trunk; 1.6's pine. */
    TALL;

    /** The shape a {@code shape} value names, or {@link #OAK} if unrecognised. */
    public static TreeShape parse(String shape) {
        if (shape == null) {
            return OAK;
        }
        return switch (shape.trim().toLowerCase(Locale.ROOT)) {
            case "conifer", "pine" -> CONIFER;
            case "tall", "tallpine", "tallconifer" -> TALL;
            // "birch" was a name of its own in 2013 and ran the oak generator
            // anyway, so it is not a shape so much as a synonym.
            default -> OAK;
        };
    }

    /** The shape a definition declares, defaulting as {@code setShape(null)} did. */
    public static TreeShape forBlock(BlockDef def) {
        return parse(def.props().getString("shape").orElse(null));
    }
}
