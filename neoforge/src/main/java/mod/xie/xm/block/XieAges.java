package mod.xie.xm.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Optional;

/**
 * The {@code age} property of a block whose number of stages the content decides.
 *
 * <p>Shared by the two families that age, which cannot share a superclass: a
 * {@link XiePlantBlock} is vanilla's {@code VegetationBlock} and a
 * {@link XieRipeningBlock} is a solid {@code Block}, and the first of those sits in
 * vanilla's hierarchy rather than this mod's. What they have in common is only this —
 * and the growth roll, which is two lines and written out in each.
 *
 * <p>A blockstate property has to be created before {@code createBlockStateDefinition}
 * runs, which happens inside the {@code Block} constructor — before any instance field
 * is assigned — so the property cannot simply be built from the config in the
 * constructor body. A subclass calls {@link #prime} in its {@code super(...)} argument,
 * reads {@link #pending} in {@code createBlockStateDefinition}, and calls
 * {@link #clear} once the superclass constructor has returned.
 */
final class XieAges {

    /** The highest age a definition may ask for, and so the most stages a block has. */
    static final int MAX = 15;

    /** One {@code age} property per maximum value, so each is created once. */
    private static final IntegerProperty[] PROPERTIES = new IntegerProperty[MAX + 1];

    static {
        for (int max = 1; max <= MAX; max++) {
            PROPERTIES[max] = IntegerProperty.create("age", 0, max);
        }
    }

    /** Carries the max age into {@code createBlockStateDefinition}. See above. */
    private static final ThreadLocal<Integer> PENDING = new ThreadLocal<>();

    private XieAges() { }

    /** A declared maximum age, held to what a property can carry. */
    static int clamp(int maxAge) {
        return Math.clamp(maxAge, 1, MAX);
    }

    /** The {@code age} property running from 0 to {@code maxAge}. */
    static IntegerProperty property(int maxAge) {
        return PROPERTIES[clamp(maxAge)];
    }

    /**
     * Publishes the max age just before {@code super(...)} is entered. Argument
     * expressions are evaluated before the superclass constructor runs, so the value
     * is in place by the time {@code createBlockStateDefinition} asks.
     */
    static BlockBehaviour.Properties prime(BlockBehaviour.Properties properties, int maxAge) {
        PENDING.set(clamp(maxAge));
        return properties;
    }

    /**
     * The property for the block under construction. A codec-built block never
     * primed, and takes the smallest.
     */
    static IntegerProperty pending() {
        Integer max = PENDING.get();
        return PROPERTIES[max == null || max == 0 ? 1 : max];
    }

    /**
     * As {@link #prime}, for a block that may have no stages at all: zero primes no
     * property, which is what a {@link XieTreeBlock} that bears nothing has.
     */
    static BlockBehaviour.Properties primeOptional(BlockBehaviour.Properties properties, int maxAge) {
        PENDING.set(maxAge <= 0 ? 0 : clamp(maxAge));
        return properties;
    }

    /** The property for the block under construction, or empty when it primed none. */
    static Optional<IntegerProperty> pendingOptional() {
        Integer max = PENDING.get();
        return max == null || max == 0 ? Optional.empty() : Optional.of(PROPERTIES[max]);
    }

    static void clear() {
        PENDING.remove();
    }
}
