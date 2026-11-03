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
 * <p>Each of those has an accessor that marks the key it came from consumed, which is
 * what lets the unread-property report see a structure that parsed and then went
 * nowhere. Reading a component field directly inside this class deliberately does not
 * count — {@link #impliedMaxMeta()} sizes a blockstate from the bounds and the faces
 * without that being anybody's use of either.
 *
 * @param origin      where it was declared
 * @param name        the declaration key, e.g. {@code tomatoBush}
 * @param displayName the {@code name} property, or the key if absent
 * @param archetype   the {@code type} property; {@code basic} when unset
 * @param override    the vanilla object this replaces, from {@code override}
 * @param drops       what it yields when broken
 * @param fruit       what a ripe leaf or bush yields when picked; {@code tree} and {@code bush} only
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
    DropTable fruit,
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
        "sapling", "furnace", "mounted", "facing", "pane", "fence", "glass",
        // Not one of JobBlock's sixteen. The 2013 stove was lost with the Cooking
        // Mod pack, so this archetype is rebuilt from the mod posting rather than
        // ported: see XieStoveBlockEntity for the recipe table it describes.
        "stove",
        // New in the port: a solid block that ages in place and becomes another,
        // which is how fresh cheese turns into a cheese block. See XieRipeningBlock.
        "ripening"
    );

    /**
     * What breaking this block yields.
     *
     * <p>A consuming read, and so are the four below it. The parser fills these in
     * for every definition it understands, which is why marking them at parse time
     * said nothing useful: {@code blazeTree} declared a {@code blockBounds} that
     * parsed perfectly and that no archetype ever asked for, and the report could not
     * see it because the reader's own read had already marked the key. Asking through
     * the accessor moves the question to the only place that can answer it — whether a
     * registrar or an emitter path actually wanted the value.
     *
     * <p>{@code expandCommonBlockParameters} read {@code drops} for every block
     * archetype and so does this: every registered block is asked whether it drops
     * itself, and every one with drops gets a loot table.
     */
    public DropTable drops() {
        props.markUsed("drops");
        return drops;
    }

    /**
     * What a ripe leaf yields when picked. Only {@code tree} consults it, as only
     * {@code doTree} read the {@code sheds} it replaces, so a definition declaring it
     * on anything else is reported.
     */
    public DropTable fruit() {
        props.markUsed("fruit");
        return fruit;
    }

    /**
     * The collision and selection box.
     *
     * <p>1.6 read this for every archetype, leaves included. Here {@code basic},
     * {@code log}, {@code tree} and the four growing archetypes consult it; a
     * {@code stem} does not, because it is vanilla's {@code StemBlock} and has no
     * class of its own to hand a shape to — the same limit that stops one being
     * {@code spikey}.
     */
    public Optional<MetaMap<Bounds>> bounds() {
        props.markUsed("blockBounds");
        return bounds;
    }

    /**
     * The sprites this block draws itself with.
     *
     * <p>Consumed by the texture copy, which runs for every registered block except a
     * {@code stem} — a stem borrows vanilla's nine melon-stem models and has no sprite
     * of its own to place. Whether the indices are actually needed depends on the
     * pack: content that migrated to one PNG per sprite ships the files and the
     * declaration is only the fallback, which is still the texture path having
     * consulted it.
     */
    public TexDef tex() {
        props.markUsed("tex", "texFile", "icon");
        return tex;
    }

    /**
     * The block whose texture fills in every face this one does not draw.
     *
     * <p>Apart from {@link #tex()} because its consumer is narrower: 1.6's
     * {@code XieTex.setTextureBase} was a fallback for any sprite lookup that came up
     * empty, and here it reaches the face entries of a cube model and nothing else. A
     * cross or a crop has one texture key pointing at its own sprite, so a
     * {@code textureBase} on one of those is dropped — and now says so.
     */
    public Optional<String> textureBase() {
        props.markUsed("textureBase");
        return tex.base();
    }

    /** {@link #tex()} without marking it consumed, for diagnostics. */
    TexDef peekTex() {
        return tex;
    }

    /** {@link #drops()} without marking it consumed, for diagnostics. */
    DropTable peekDrops() {
        return drops;
    }

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
