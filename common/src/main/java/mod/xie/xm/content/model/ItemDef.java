package mod.xie.xm.content.model;

import java.util.Optional;

/**
 * One item declaration from an {@code "items"} section.
 *
 * @param origin      where it was declared
 * @param name        the declaration key, e.g. {@code tomatoSeeds}
 * @param displayName the {@code name} property, or the key if absent
 * @param archetype   the {@code type} property; {@code basic} when unset
 * @param override    the vanilla object this replaces, from {@code override}
 * @param tex         spritesheet assignment ({@code texFile} plus {@code icon})
 * @param props       every remaining property
 */
public record ItemDef(
    Origin origin,
    String name,
    String displayName,
    String archetype,
    Optional<ItemRef> override,
    TexDef tex,
    PropertyMap props
) {

    public static final String DEFAULT_ARCHETYPE = "basic";

    /** Archetypes the original engine dispatched on, from {@code JobItem.doWork}. */
    public static final java.util.Set<String> KNOWN_ARCHETYPES = java.util.Set.of(
        "basic", "food", "seeds", "tool", "sword", "bow", "armor", "armour",
        "bucket", "potion", "block", "stick", "set", "fertiliser", "interface"
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

    /** Convenience for the food archetype; empty when this is not edible. */
    public Optional<Integer> hunger() {
        return props.peek("hunger").flatMap(PropertyMap::asInt);
    }

    /** Convenience for the food archetype; empty when this is not edible. */
    public Optional<Float> nourishment() {
        return props.peek("nourishment").flatMap(PropertyMap::asFloat);
    }
}
