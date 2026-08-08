package mod.xie.xm.block;

import com.mojang.serialization.MapCodec;

import mod.xie.xm.content.model.Bounds;
import mod.xie.xm.content.model.MetaMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code bush} archetype: a perennial that regrows after harvest.
 *
 * <p>This is what distinguishes a bush from a crop in Xie content, and it comes
 * straight from {@code XieBlockBush.onBlockDestroyedByPlayer}:
 *
 * <pre>
 *   if (l == maxGrowth) {
 *       int newMeta = maxGrowth - 3;
 *       Xie.setBlockAndMeta(world, i, j, k, this.blockID, newMeta);
 *   }
 * </pre>
 *
 * <p>Break a ripe bush and it is not removed — it drops its fruit and drops back
 * three growth stages. A crop, by contrast, is annual: harvesting destroys it and
 * you replant from seed. Bushes also accept natural ground ({@code growsOn} is
 * typically {@code farmland,dirt,grass}) where crops want farmland.
 */
public class XieBushBlock extends VegetationBlock {

    /**
     * How far a ripe bush falls back when harvested, from the original's
     * {@code maxGrowth - 3}.
     */
    public static final int REGROW_SETBACK = 3;

    /** Base denominator for the growth roll; {@code growthRate} scales it. */
    public static final int GROWTH_DENOMINATOR = 25;

    /**
     * Configuration parsed from a {@code .xie} block definition.
     *
     * @param maxAge     highest growth stage, clamped to 1..15
     * @param growthRate percentage; 100 is the default speed, higher is faster
     * @param growsOn    fully-qualified block ids this may be planted on
     * @param shapes     collision/selection box per age, may be empty
     */
    public record Config(int maxAge, int growthRate, Set<String> growsOn, Optional<MetaMap<Bounds>> shapes) {

        public Config {
            maxAge = Math.clamp(maxAge, 1, 15);
            growthRate = Math.max(1, growthRate);
            growsOn = Set.copyOf(growsOn);
        }

        /** Used only to satisfy the block codec; never placed in a world. */
        public static final Config PLACEHOLDER =
            new Config(1, 100, Set.of(), Optional.empty());

        public int regrowAge() {
            return Math.max(0, maxAge - REGROW_SETBACK);
        }
    }

    public static final MapCodec<XieBushBlock> CODEC =
        simpleCodec(properties -> new XieBushBlock(properties, Config.PLACEHOLDER));

    /**
     * One {@code age} property per maximum value. A blockstate property has to be
     * created before {@link #createBlockStateDefinition} runs, which happens inside
     * the superclass constructor — before any instance field is assigned — so the
     * property cannot simply be built from the config in the constructor body.
     */
    private static final IntegerProperty[] AGE_PROPERTIES = new IntegerProperty[16];

    static {
        for (int max = 1; max < AGE_PROPERTIES.length; max++) {
            AGE_PROPERTIES[max] = IntegerProperty.create("age", 0, max);
        }
    }

    /** Carries the max age into {@link #createBlockStateDefinition}. See above. */
    private static final ThreadLocal<Integer> PENDING_MAX_AGE = new ThreadLocal<>();

    private final Config config;
    private final IntegerProperty ageProperty;
    private final List<VoxelShape> shapesByAge;

    public XieBushBlock(BlockBehaviour.Properties properties, Config config) {
        super(prime(properties, config.maxAge()));
        PENDING_MAX_AGE.remove();

        this.config = config;
        this.ageProperty = AGE_PROPERTIES[config.maxAge()];
        this.shapesByAge = buildShapes(config);
        registerDefaultState(this.stateDefinition.any().setValue(this.ageProperty, 0));
    }

    /**
     * Publishes the max age just before {@code super(...)} is entered. Argument
     * expressions are evaluated before the superclass constructor runs, so the
     * value is in place by the time {@link #createBlockStateDefinition} asks.
     */
    private static BlockBehaviour.Properties prime(BlockBehaviour.Properties properties, int maxAge) {
        PENDING_MAX_AGE.set(Math.clamp(maxAge, 1, 15));
        return properties;
    }

    private static List<VoxelShape> buildShapes(Config config) {
        if (config.shapes().isEmpty()) {
            return List.of();
        }
        MetaMap<Bounds> bounds = config.shapes().get();
        VoxelShape[] shapes = new VoxelShape[config.maxAge() + 1];
        for (int age = 0; age <= config.maxAge(); age++) {
            Bounds box = bounds.get(age).orElse(null);
            if (box == null) {
                shapes[age] = null;
                continue;
            }
            float[] px = box.toPixels();
            shapes[age] = Block.box(px[0], px[1], px[2], px[3], px[4], px[5]);
        }
        return java.util.Arrays.asList(shapes);
    }

    public Config config() {
        return config;
    }

    public IntegerProperty ageProperty() {
        return ageProperty;
    }

    @Override
    protected MapCodec<XieBushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        Integer max = PENDING_MAX_AGE.get();
        builder.add(AGE_PROPERTIES[max == null ? 1 : max]);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (shapesByAge.isEmpty()) {
            return super.getShape(state, level, pos, context);
        }
        int age = state.getValue(ageProperty);
        VoxelShape shape = age < shapesByAge.size() ? shapesByAge.get(age) : null;
        return shape != null ? shape : super.getShape(state, level, pos, context);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        if (config.growsOn().isEmpty()) {
            return super.mayPlaceOn(state, level, pos);
        }
        return config.growsOn().contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(ageProperty);
        if (age >= config.maxAge()) {
            return;
        }
        // growthRate is a percentage where 100 is the default speed, so a higher
        // rate means a smaller denominator and a more frequent successful roll.
        int denominator = Math.max(1, GROWTH_DENOMINATOR * 100 / config.growthRate());
        if (random.nextInt(denominator) == 0) {
            level.setBlock(pos, state.setValue(ageProperty, age + 1), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (level.isClientSide()) {
            return;
        }
        // The perennial behaviour: a ripe bush is knocked back rather than removed.
        if (state.getValue(ageProperty) >= config.maxAge()) {
            level.setBlockAndUpdate(pos, defaultBlockState().setValue(ageProperty, config.regrowAge()));
        }
    }
}
