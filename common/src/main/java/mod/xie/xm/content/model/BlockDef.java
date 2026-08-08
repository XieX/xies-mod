package mod.xie.xm.content.model;

import java.util.Map;
import java.util.Optional;

/**
 * One block declaration from a {@code "blocks"} section.
 *
 * <p>Structural properties — drops, textures, bounds — are normalised here.
 * Everything else stays in {@link #props()} for the archetype to interpret, so
 * adding a property to an archetype does not mean touching the parser.
 *
 * @param origin      where it was declared
 * @param name        the declaration key, e.g. {@code tomatoBush}
 * @param displayName the {@code name} property, or the key if absent
 * @param archetype   the {@code type} property; {@code basic} when unset
 * @param override    the vanilla object this replaces, from {@code override}
 * @param drops       what it yields when broken
 * @param tex         spritesheet assignment
 * @param bounds      collision/selection box, possibly varying by metadata
 * @param props       every remaining property
 */
public record BlockDef(
    Origin origin,
    String name,
    String displayName,
    String archetype,
    Optional<ItemRef> override,
    DropTable drops,
    TexDef tex,
    Optional<MetaMap<Bounds>> bounds,
    PropertyMap props
) {

    /** The archetype assumed when a block declares no {@code type}. */
    public static final String DEFAULT_ARCHETYPE = "basic";

    /**
     * Archetypes the original engine dispatched on, from {@code JobBlock.doWork}.
     * Kept as data rather than an enum so third-party packs can register their own
     * without the parser rejecting them.
     */
    public static final java.util.Set<String> KNOWN_ARCHETYPES = java.util.Set.of(
        "basic", "plant", "crop", "stem", "grass", "chest", "bush", "tree", "log",
        "sapling", "furnace", "mounted", "facing", "pane", "fence", "glass"
    );

    public boolean isOverride() {
        return override.isPresent();
    }

    public boolean hasKnownArchetype() {
        return KNOWN_ARCHETYPES.contains(archetype);
    }

    public String qualifiedName() {
        return origin.qualifiedName();
    }

    /**
     * How many distinct states this block needs, inferred from the properties that
     * describe one state each: collision bounds and per-face textures.
     *
     * <p>Drop chances are deliberately excluded. They routinely use open-ended
     * ranges — {@code "4+"} means "ripe", not "this block has sixteen states" —
     * so counting them would inflate every crop to the full metadata range.
     *
     * <p>The registrar uses this to size the age property when it creates the
     * blockstate, so getting it wrong means generating fifteen unused states.
     */
    public int impliedMaxMeta() {
        int highest = bounds.map(MetaMap::highestDeclaredMeta).orElse(0);
        for (Map.Entry<String, MetaMap<Integer>> face : tex.faces().entrySet()) {
            highest = Math.max(highest, face.getValue().highestDeclaredMeta());
        }
        return highest;
    }
}
