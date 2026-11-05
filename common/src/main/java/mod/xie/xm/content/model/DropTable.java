package mod.xie.xm.content.model;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * What a block yields when broken.
 *
 * <p>Two source shapes, both normalised to the same model:
 *
 * <pre>
 *   "drops": "potash"
 *   "drops": "seeds,wheat"
 *   "drops": {
 *     "tomato":      { "chance": { "0-3": 0, "4+": 50 }, "amount": 2 },
 *     "tomatoSeeds": { "chance": { "0": 0, "1": 20 },    "amount": 3 }
 *   }
 * </pre>
 *
 * <p>Chance is a percentage, per metadata value. The registrar turns each entry
 * into a loot table pool with a {@code match_block} condition on age.
 */
public record DropTable(List<Drop> drops) {

    public static final DropTable EMPTY = new DropTable(List.of());

    public DropTable {
        drops = List.copyOf(drops);
    }

    public boolean isEmpty() {
        return drops.isEmpty();
    }

    /**
     * One dropped stack.
     *
     * @param item    what drops
     * @param chance  percentage chance per metadata value; absent means always, and
     *                a value the map leaves out never drops
     * @param amount  maximum number dropped, 1 unless the source said otherwise
     * @param min     how many of {@code amount} drop without rolling, wherever the
     *                chance is above zero; 0 unless the source said otherwise
     * @param extra   any further keys the archetype understands
     */
    public record Drop(ItemRef item, Optional<MetaMap<Integer>> chance, int amount, int min,
                       PropertyMap extra) {

        public Drop {
            amount = Math.max(1, Math.max(amount, min));
            min = Math.max(0, min);
        }

        public static Drop always(ItemRef item) {
            return new Drop(item, Optional.empty(), 1, 0, PropertyMap.empty());
        }

        /** What one drop yields on average where its chance is {@code percent}. */
        public double average(int percent) {
            return percent <= 0 ? 0 : min + (amount - min) * percent / 100.0;
        }

        /**
         * Percentage chance for a metadata value: 100 when unconditional, and 0 for a
         * value a declared chance map leaves out. 1.6's {@code Drop.metaChance} array
         * held 0 there too, except at metadata 0, which the constructor had already set
         * to 100 before the map was read; that one exception is not carried, because
         * {@code "3+": 50} cannot have meant a certain drop from a seedling.
         */
        public int chanceFor(int meta) {
            return chance.map(map -> map.get(meta).orElse(0)).orElse(100);
        }

        public boolean isUnconditional() {
            return chance.isEmpty();
        }
    }

    public List<Drop> unmodifiable() {
        return Collections.unmodifiableList(drops);
    }
}
