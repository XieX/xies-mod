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
 * into a loot table pool with a {@code block_state_property} condition on age.
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
     * @param chance  percentage chance per metadata value; absent means always
     * @param amount  maximum number dropped, 1 unless the source said otherwise
     * @param extra   any further keys the archetype understands
     */
    public record Drop(ItemRef item, Optional<MetaMap<Integer>> chance, int amount, PropertyMap extra) {

        public Drop {
            amount = Math.max(1, amount);
        }

        public static Drop always(ItemRef item) {
            return new Drop(item, Optional.empty(), 1, PropertyMap.empty());
        }

        /** Percentage chance for a metadata value; 100 when unconditional. */
        public int chanceFor(int meta) {
            return chance.flatMap(map -> map.get(meta)).orElse(100);
        }

        public boolean isUnconditional() {
            return chance.isEmpty();
        }
    }

    public List<Drop> unmodifiable() {
        return Collections.unmodifiableList(drops);
    }
}
