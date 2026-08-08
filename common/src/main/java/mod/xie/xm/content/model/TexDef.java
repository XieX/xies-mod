package mod.xie.xm.content.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A texture assignment, expressed the way 2013 content expressed it: an index into
 * a spritesheet.
 *
 * <p>Source shapes:
 *
 * <pre>
 *   "texFile": "xie_crops.png", "icon": 22            item, single sprite
 *   "tex": { "texFile": "xie_misc.png", "all": 21 }   block, one sprite everywhere
 *   "tex": { "texFile": "...", "sides": 136, "ends": 137 }
 *   "tex": { "texFile": "...", "all": { "0": 16, "1": 17 } }   varying by metadata
 * </pre>
 *
 * <p>Sheet indices are meaningless to a modern client, which wants one PNG per
 * sprite stitched into an atlas. Keeping the indices in the model rather than
 * resolving them at parse time is deliberate: it lets the build-time slicer and
 * the runtime compatibility path share exactly one interpretation of the format.
 *
 * @param sheet     the spritesheet filename, e.g. {@code xie_crops.png}
 * @param faces     face name to sprite index, possibly varying by metadata
 * @param base      optional {@code textureBase} this definition inherits from
 */
public record TexDef(Optional<String> sheet, Map<String, MetaMap<Integer>> faces, Optional<String> base) {

    /** Face keys the format recognises. Anything else is passed through as-is. */
    public static final String FACE_ALL = "all";
    public static final String FACE_SIDES = "sides";
    public static final String FACE_ENDS = "ends";
    public static final String FACE_TOP = "top";
    public static final String FACE_BOTTOM = "bottom";
    public static final String FACE_FRONT = "front";
    public static final String FACE_BACK = "back";

    /** The single-sprite key used by items ({@code "icon"}). */
    public static final String FACE_ICON = "icon";

    public static final TexDef EMPTY = new TexDef(Optional.empty(), Map.of(), Optional.empty());

    public TexDef {
        faces = Collections.unmodifiableMap(new LinkedHashMap<>(faces));
    }

    public boolean isEmpty() {
        return sheet.isEmpty() && faces.isEmpty() && base.isEmpty();
    }

    public Optional<MetaMap<Integer>> face(String name) {
        return Optional.ofNullable(faces.get(name));
    }

    /**
     * The sprite index to use for a face at a metadata value, falling back to
     * {@code all} and then to the item icon.
     */
    public Optional<Integer> spriteFor(String faceName, int meta) {
        Optional<Integer> exact = face(faceName).flatMap(map -> map.get(meta));
        if (exact.isPresent()) {
            return exact;
        }
        Optional<Integer> all = face(FACE_ALL).flatMap(map -> map.get(meta));
        if (all.isPresent()) {
            return all;
        }
        return face(FACE_ICON).flatMap(map -> map.get(meta));
    }

    /** Every distinct sprite index this definition references. */
    public java.util.SortedSet<Integer> referencedSprites() {
        java.util.SortedSet<Integer> indices = new java.util.TreeSet<>();
        for (MetaMap<Integer> map : faces.values()) {
            for (MetaMap.Entry<Integer> entry : map.entries()) {
                indices.add(entry.value());
            }
        }
        return indices;
    }
}
