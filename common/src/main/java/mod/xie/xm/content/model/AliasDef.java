package mod.xie.xm.content.model;

import java.util.List;

/**
 * One entry from an {@code "aliases"} section — a named set of interchangeable items.
 *
 * <pre>
 *   "aliases": {
 *     "protein": "porkchopCooked,beefCooked,tofu",
 *     "salady":  "lettuce,tomato,onion"
 *   }
 * </pre>
 *
 * <p>Referenced from recipes as {@code $protein}. The original expanded these into
 * the cartesian product of concrete recipes at load time — a recipe with three
 * alias slots over six-item aliases became 216 registered recipes.
 *
 * <p>Modern Minecraft has item tags, so an alias becomes one tag and a recipe
 * referencing it stays a single recipe. That is a large reduction in registered
 * objects and is one of the clearest wins from the port.
 *
 * <p>Aliases are additive: several files may contribute to the same name, matching
 * {@code JobAlias}, which called {@code addAll} on an existing entry.
 */
public record AliasDef(Origin origin, String name, List<ItemRef> members) {

    public AliasDef {
        members = List.copyOf(members);
    }

    /** This alias with another declaration of the same name folded in. */
    public AliasDef merge(AliasDef other) {
        List<ItemRef> combined = new java.util.ArrayList<>(members);
        for (ItemRef member : other.members) {
            if (!combined.contains(member)) {
                combined.add(member);
            }
        }
        return new AliasDef(origin, name, combined);
    }
}
