package mod.xie.xm.content.model;

import java.util.Locale;
import java.util.Optional;

/**
 * An inclusive range of legacy block metadata (or crop age) values.
 *
 * <p>Xie content addresses per-state values with keys like {@code "0-3"},
 * {@code "4+"}, {@code "2"} or {@code "all"}. Metadata was a 4-bit field, so
 * values are clamped to 0..15 exactly as {@code Xie.validateMeta} did.
 *
 * <p>{@code openEnded} records whether the author wrote {@code "4+"} or
 * {@code "all"} rather than a closed range. It cannot be recovered from the
 * bounds afterwards: real content contains {@code "14-15"}, a deliberate closed
 * range that ends at the maximum, and conflating it with {@code "14+"} changes
 * how many states the block is given.
 *
 * <p>Modern Minecraft has no metadata; these ranges are translated to blockstate
 * property values by the registrar.
 */
public record MetaRange(int min, int max, boolean openEnded) implements Comparable<MetaRange> {

    public static final int MIN_META = 0;
    public static final int MAX_META = 15;

    /** Every metadata value, from an {@code "all"} key. */
    public static final MetaRange ALL = new MetaRange(MIN_META, MAX_META, true);

    public MetaRange {
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
        min = clamp(min);
        max = clamp(max);
    }

    /** A closed range. */
    public MetaRange(int min, int max) {
        this(min, max, false);
    }

    public static int clamp(int meta) {
        return Math.min(MAX_META, Math.max(MIN_META, meta));
    }

    public static MetaRange single(int meta) {
        return new MetaRange(meta, meta, false);
    }

    /** A range of the form {@code "n+"} — n onwards. */
    public static MetaRange atLeast(int meta) {
        return new MetaRange(meta, MAX_META, true);
    }

    /**
     * Parses a legacy range key. Returns empty rather than throwing, so one bad
     * key in a third-party pack degrades to a diagnostic instead of a hard failure.
     *
     * <pre>
     *   "all"    -&gt; 0..15, open
     *   "4+"     -&gt; 4..15, open
     *   "0-3"    -&gt; 0..3,  closed
     *   "14-15"  -&gt; 14..15, closed
     *   "2"      -&gt; 2..2,  closed
     * </pre>
     *
     * <p>The original also accepted bucket-content names here via
     * {@code XieItemBucket.isValidContents}. That is engine-specific rather than
     * format-level, so it belongs in the registrar, not the parser.
     */
    public static Optional<MetaRange> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (text.equals("all") || text.equals("*")) {
            return Optional.of(ALL);
        }
        try {
            if (text.endsWith("+")) {
                return Optional.of(atLeast(Integer.parseInt(text.substring(0, text.length() - 1).trim())));
            }
            int dash = text.indexOf('-', 1);
            if (dash > 0) {
                int low = Integer.parseInt(text.substring(0, dash).trim());
                int high = Integer.parseInt(text.substring(dash + 1).trim());
                return Optional.of(new MetaRange(low, high));
            }
            return Optional.of(single(Integer.parseInt(text)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public boolean contains(int meta) {
        return meta >= min && meta <= max;
    }

    public boolean isSingle() {
        return min == max && !openEnded;
    }

    public int size() {
        return max - min + 1;
    }

    /**
     * The highest metadata value this range says actually exists.
     *
     * <p>An open-ended range means "from here onwards", so {@code "8+"} tells you
     * the author considers 8 the final stage — not that the block has sixteen of
     * them. Counting the literal maximum instead turns a nine-stage bush into a
     * sixteen-state block with seven states nothing ever references. A closed
     * {@code "14-15"} does mean 15, and is reported as such.
     */
    public int boundedMax() {
        return openEnded ? min : max;
    }

    @Override
    public int compareTo(MetaRange other) {
        int byMin = Integer.compare(min, other.min);
        return byMin != 0 ? byMin : Integer.compare(max, other.max);
    }

    @Override
    public String toString() {
        if (openEnded) {
            return min == MIN_META ? "all" : min + "+";
        }
        return isSingle() ? Integer.toString(min) : min + "-" + max;
    }
}
