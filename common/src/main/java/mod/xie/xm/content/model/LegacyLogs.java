package mod.xie.xm.content.model;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The four wood types 1.6 packed into one log block.
 *
 * <p>Until 1.7 there was a single log id whose metadata picked the wood — 0 oak,
 * 1 spruce, 2 birch, 3 jungle — so a {@code tree} definition says which trunk it
 * wants with two properties rather than one: {@code trunk} names the block and
 * {@code trunkMeta} picks the wood. Those are separate blocks now, so the pair has
 * to collapse back into a single block id.
 *
 * <p>A tree can write it two ways. {@code "trunk": "log", "trunkMeta": "3"} names
 * both halves — a jungle trunk. {@code fruitLeaves} declares no {@code trunk} at
 * all and only {@code "trunkMeta": "0,0,2,1"}, one entry per subtype, because
 * {@code XieBlockLeaves} initialised {@code trunkID} to the log block already: the
 * apple and orange trees are oak, the lemon birch and the avocado spruce.
 *
 * <p>Kept apart from {@link LegacyBlockIds} for the same reason that table is kept
 * apart from {@link ResourceNames}: this one answers a question about a
 * <em>pair</em> of properties, and only for the one block whose metadata meant a
 * different material rather than a different state.
 */
public final class LegacyLogs {

    /** The wood types in metadata order, as {@code Block.wood}'s subtypes. */
    private static final List<String> BY_META =
        List.of("oak_log", "spruce_log", "birch_log", "jungle_log");

    /**
     * The names 1.6 content could call the log block by. {@code log} is its
     * unlocalized name and {@code 17} its numeric id; {@code wood} is <em>not</em>
     * one of them — that was the unlocalized name of the planks.
     */
    private static final List<String> NAMES = List.of("log", "17");

    /** The trunk a {@code tree} gets when it declares neither property. */
    public static final String DEFAULT = "oak_log";

    private LegacyLogs() { }

    /**
     * Whether a {@code trunk} value names the single 1.6 log block, and so leaves
     * {@code trunkMeta} to decide which wood it means.
     */
    public static boolean isLegacyLog(String name) {
        return name != null && NAMES.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    /** The modern log block path for a wood metadata value, or empty if there is none. */
    public static Optional<String> path(int meta) {
        return meta >= 0 && meta < BY_META.size() ? Optional.of(BY_META.get(meta)) : Optional.empty();
    }

    /**
     * The modern log block path for a {@code trunkMeta} value.
     *
     * <p>Out-of-range values fall back to oak, as {@code XieBlockLeaves.getTrunkMeta}
     * did — it returned 0 for any metadata past the end of the declared array.
     */
    public static String pathOrOak(int meta) {
        return path(meta).orElse(DEFAULT);
    }
}
