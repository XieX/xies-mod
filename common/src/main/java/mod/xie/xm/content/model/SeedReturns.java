package mod.xie.xm.content.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Growing blocks that give back at least the seed that planted them the moment they
 * are planted.
 *
 * <p>A drop's {@code amount} is independent rolls at {@code chance} percent each, less
 * the {@code min} that need no roll, so what breaking a block at age 0 yields of an
 * item, on average, is {@link DropTable.Drop#average} at the age-0 chance. When that item is also the seed whose {@code growsInto} names the
 * block, one or more means planting and breaking costs nothing, and more than one
 * makes seed from no growing at all. A chance map is easy to get wrong without
 * anything failing to load, so this is the load saying so.
 *
 * <p>Asked only of the archetypes a seed plants, and only within one pack, which is
 * where the registrar looks for a block's seed too. Nothing here is consumed: this is
 * a question about the content, not a use of it, so it reads past the accessors that
 * would mark {@code drops} and {@code growsInto} as wanted.
 */
public final class SeedReturns {

    private SeedReturns() { }

    /** The archetypes a {@code seeds} item plants and that age from 0. */
    private static final Set<String> GROWING = Set.of("plant", "bush", "crop", "stem");

    /** {@code JobItem.doWork} sent both spellings to the one {@code doSeeds}. */
    private static final Set<String> SEEDS = Set.of("seeds", "seed");

    /**
     * One block that drops its own seed too generously at age 0.
     *
     * @param block  the growing block
     * @param seed   the item that plants it and that it drops
     * @param chance the age-0 chance, in percent, capped at 100
     * @param amount how many it drops at most
     * @param min    how many of those drop without rolling
     */
    public record Finding(BlockDef block, ItemDef seed, int chance, int amount, int min) {

        /** How many seeds an age-0 break yields on average. */
        public double perBreak() {
            return chance <= 0 ? 0 : min + (amount - min) * chance / 100.0;
        }
    }

    /** Every growing block in one pack that pays back its seed at age 0. */
    public static List<Finding> in(Map<String, BlockDef> blocks, Map<String, ItemDef> items) {
        List<Finding> findings = new ArrayList<>();
        for (BlockDef block : blocks.values()) {
            if (!GROWING.contains(block.archetype())) {
                continue;
            }
            for (DropTable.Drop drop : block.peekDrops().drops()) {
                ItemRef ref = drop.item();
                if (ref.isGroup() || ref.isVanilla()) {
                    continue;
                }
                ItemDef seed = items.get(ref.name());
                if (seed == null || !SEEDS.contains(seed.archetype())
                    || seed.props().peekString("growsInto").map(String::trim)
                        .filter(block.name()::equals).isEmpty()) {
                    continue;
                }
                int chance = Math.min(100, drop.chanceFor(0));
                if (drop.average(chance) >= 1) {
                    findings.add(new Finding(block, seed, chance, drop.amount(), drop.min()));
                }
            }
        }
        return findings;
    }
}
