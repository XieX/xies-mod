package mod.xie.xm.content.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A value that may vary per metadata value.
 *
 * <p>Covers both shapes the {@code .xie} format allows for the same property:
 *
 * <pre>
 *   "blockBounds": "0,0,0,1,0.2,1"                  -&gt; uniform
 *   "blockBounds": { "0": "...", "1-3": "..." }     -&gt; varying
 * </pre>
 *
 * <p>Lookup is <em>last match wins</em>, so a later, narrower entry overrides an
 * earlier broad one. Legacy content uses disjoint ranges almost everywhere, so
 * this only matters for hand-written packs.
 */
public final class MetaMap<T> {

    public record Entry<T>(MetaRange range, T value) {
        public Entry {
            Objects.requireNonNull(range, "range");
        }
    }

    private final List<Entry<T>> entries = new ArrayList<>();
    private final boolean uniform;

    private MetaMap(boolean uniform) {
        this.uniform = uniform;
    }

    /** A map holding one value for every metadata value. */
    public static <T> MetaMap<T> uniform(T value) {
        MetaMap<T> map = new MetaMap<>(true);
        map.entries.add(new Entry<>(MetaRange.ALL, value));
        return map;
    }

    /** An empty map to be filled in with {@link #put}. */
    public static <T> MetaMap<T> varying() {
        return new MetaMap<>(false);
    }

    public MetaMap<T> put(MetaRange range, T value) {
        entries.add(new Entry<>(range, value));
        return this;
    }

    /** True when the source declared a single scalar rather than a range map. */
    public boolean isUniform() {
        return uniform;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<Entry<T>> entries() {
        return Collections.unmodifiableList(entries);
    }

    public Optional<T> get(int meta) {
        T found = null;
        for (Entry<T> entry : entries) {
            if (entry.range().contains(meta)) {
                found = entry.value();
            }
        }
        return Optional.ofNullable(found);
    }

    /**
     * The value for metadata 0, which is what a uniform map and most single-state
     * blocks resolve to. Useful when the registrar needs one representative value.
     */
    public Optional<T> first() {
        return entries.isEmpty() ? Optional.empty() : Optional.ofNullable(entries.get(0).value());
    }

    /** The highest metadata value any entry literally covers. */
    public int highestMeta() {
        int highest = 0;
        for (Entry<T> entry : entries) {
            highest = Math.max(highest, entry.range().max());
        }
        return highest;
    }

    /**
     * The highest metadata value the entries say actually exists, treating an
     * open-ended range as ending where it starts. See {@link MetaRange#boundedMax()}.
     */
    public int highestDeclaredMeta() {
        int highest = 0;
        for (Entry<T> entry : entries) {
            highest = Math.max(highest, entry.range().boundedMax());
        }
        return highest;
    }

    @Override
    public String toString() {
        if (uniform) {
            return String.valueOf(first().orElse(null));
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(entries.get(i).range()).append('=').append(entries.get(i).value());
        }
        return sb.append('}').toString();
    }
}
