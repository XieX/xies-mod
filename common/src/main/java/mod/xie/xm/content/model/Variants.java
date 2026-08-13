package mod.xie.xm.content.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a legacy multi-subtype definition into one modern object per subtype.
 *
 * <p>In 1.6 a single block or item id carried up to sixteen subtypes distinguished
 * by a damage value, and a definition declared them all at once:
 *
 * <pre>
 *   "fruitSeed": {
 *     "name":  "Fruit Seed",
 *     "names": "Apple Seeds,Orange Seeds,Lemon Seeds,Avocado Seed",
 *     ...
 *   }
 * </pre>
 *
 * <p>Content then referred to a subtype through the legacy {@code base:count:meta}
 * form — {@code fruitSeed:1:2} is the lemon one. Damage-value subtypes are gone, so
 * each entry in {@code names} has to become its own registered object with its own
 * id, and every {@code base:count:meta} reference has to resolve to that id.
 *
 * <p>The id comes from the subtype's own display name rather than from
 * {@code base_<meta>}, because the content already agrees with itself on what those
 * names are: the aliases packs declare for their subtypes reduce to exactly the same
 * path. {@code "Apple Seeds"} and the alias {@code appleSeeds} both give
 * {@code apple_seeds}, so a reference written either way lands on the same object
 * and nothing needs a lookup table. Where a display name yields nothing usable the
 * {@code base_<meta>} form is the fallback.
 *
 * <p>The registrar and both pack emitters have to agree on these ids exactly, for
 * the same reason {@link ResourceNames} is shared: an item registered under one name
 * whose model is written under another is invisible.
 */
public final class Variants {

    /** The property that declares subtypes, one display name per metadata value. */
    public static final String PROPERTY = "names";

    /** Legacy metadata was four bits, so sixteen subtypes was the hard ceiling. */
    public static final int MAX_SUBTYPES = 16;

    private Variants() { }

    /**
     * The subtype display names a definition declares, or an empty list when it
     * declares none and is a single object.
     *
     * <p>A one-entry {@code names} is not a split: it describes the same single
     * object the definition would be anyway.
     */
    public static List<String> namesIn(PropertyMap props) {
        List<String> names = props.getStringList(PROPERTY);
        if (names.size() < 2) {
            return List.of();
        }
        return List.copyOf(names.subList(0, Math.min(names.size(), MAX_SUBTYPES)));
    }

    /** Whether {@code names} declared more than one subtype. */
    public static boolean isSplit(PropertyMap props) {
        return !namesIn(props).isEmpty();
    }

    /**
     * The id path for one subtype: its display name reduced to an id, falling back
     * to {@code <definition>_<meta>} when that yields nothing usable.
     */
    public static String pathFor(String definitionName, List<String> names, int meta) {
        String derived = meta >= 0 && meta < names.size()
            ? ResourceNames.toPath(names.get(meta))
            : "";
        return derived.isEmpty() || derived.equals("unnamed")
            ? ResourceNames.toPath(definitionName) + "_" + meta
            : derived;
    }

    /**
     * Every id path a definition registers, indexed by metadata value. A definition
     * with no subtypes yields the single path it would have had regardless, so
     * callers can treat both the same way.
     */
    public static List<String> pathsFor(String definitionName, List<String> names) {
        if (names.isEmpty()) {
            return List.of(ResourceNames.toPath(definitionName));
        }
        List<String> paths = new ArrayList<>(names.size());
        for (int meta = 0; meta < names.size(); meta++) {
            paths.add(pathFor(definitionName, names, meta));
        }
        return List.copyOf(paths);
    }
}
