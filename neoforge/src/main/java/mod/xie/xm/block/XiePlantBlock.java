package mod.xie.xm.block;

import com.mojang.serialization.MapCodec;

import mod.xie.xm.content.model.Bounds;
import mod.xie.xm.content.model.MetaMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code plant} archetype, and the base the other growing archetypes extend.
 *
 * <p>This mirrors the original's {@code XieBlockPlant}, which was both a concrete
 * archetype ({@code lettucePlant}, {@code onionPlant}) and the class
 * {@code XieBlockBush} and {@code XieBlockCrop} inherited from. Everything a
 * growing block needs regardless of kind lives here: the age property, the
 * per-age collision boxes, where it may be planted, how fast it advances, how
 * tall it stacks, and bone meal.
 *
 * <p>The three archetypes differ in exactly two places, and both are overridable:
 *
 * <ul>
 *   <li>{@link #growthDenominator} — {@code crop} folds in vanilla's
 *       farmland-adjacency bonus, so a crop in a well-tended field grows faster
 *       than the same crop alone on dry soil. A plant or bush does not care.</li>
 *   <li>{@code useWithoutItem} — {@code bush} is perennial and regrows after a
 *       harvest. A plant and a crop are annual: you break them and replant.</li>
 * </ul>
 */
public class XiePlantBlock extends VegetationBlock implements BonemealableBlock {

    /**
     * Denominator for the growth roll at {@code growthRate} 100, chosen to match
     * vanilla's {@code 25 / speed}. A higher {@code growthRate} divides it down.
     */
    public static final int GROWTH_DENOMINATOR = 25;

    /**
     * Light needed to grow when the definition declares no {@code lightLevel},
     * matching {@code CropBlock.randomTick}.
     *
     * <p>The original went further and <em>destroyed</em> a plant standing below
     * {@code lightLevel} that could not see the sky ({@code XieBlockPlant.canBlockStay}).
     * Refusing to grow is the same rule where it matters and does not delete a
     * player's field when they roof it over, so only the growth half is kept.
     */
    public static final int MIN_GROWTH_LIGHT = 9;

    /**
     * Configuration parsed from a block definition.
     *
     * @param maxAge       highest growth stage, clamped to 1..15
     * @param growthRate   percentage; 100 is the default speed, higher is faster
     * @param growthHeight how many of this block may stack; 1 is a single block
     * @param growsOn      fully-qualified block ids this may be planted on
     * @param shapes       collision/selection box per age, may be empty
     * @param fertilisable whether bone meal works on it; {@code true} unless the
     *                     definition says otherwise, as {@code XieBlockPlant}'s field
     *                     was initialised
     * @param spikey       whether standing in it hurts; see {@link XieSpikes}
     * @param growthLight  raw brightness needed to grow, 0..15
     */
    public record Config(int maxAge, int growthRate, int growthHeight,
                         Set<String> growsOn, Optional<MetaMap<Bounds>> shapes,
                         boolean fertilisable, boolean spikey, int growthLight) {

        public Config {
            maxAge = XieAges.clamp(maxAge);
            growthLight = Math.clamp(growthLight, 0, 15);
            growthRate = Math.max(1, growthRate);
            growthHeight = Math.max(1, growthHeight);
            growsOn = Set.copyOf(growsOn);
        }

        /** Used only to satisfy the block codec; never placed in a world. */
        public static final Config PLACEHOLDER =
            new Config(1, 100, 1, Set.of(), Optional.empty(), true, false, MIN_GROWTH_LIGHT);

        /** Whether this block stacks, and so may stand on another of its own kind. */
        public boolean isTall() {
            return growthHeight > 1;
        }
    }

    public static final MapCodec<XiePlantBlock> CODEC =
        simpleCodec(properties -> new XiePlantBlock(properties, Config.PLACEHOLDER));

    private final Config config;
    private final IntegerProperty ageProperty;
    private final List<VoxelShape> shapesByAge;

    /** The age property is built before this constructor's body runs; see {@link XieAges}. */
    public XiePlantBlock(BlockBehaviour.Properties properties, Config config) {
        super(XieAges.prime(properties, config.maxAge()));
        XieAges.clear();

        this.config = config;
        this.ageProperty = XieAges.property(config.maxAge());
        this.shapesByAge = buildShapes(config);
        registerDefaultState(this.stateDefinition.any().setValue(this.ageProperty, 0));
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
        return Arrays.asList(shapes);
    }

    public Config config() {
        return config;
    }

    public IntegerProperty ageProperty() {
        return ageProperty;
    }

    public int age(BlockState state) {
        return state.getValue(ageProperty);
    }

    public boolean isRipe(BlockState state) {
        return age(state) >= config.maxAge();
    }

    @Override
    protected MapCodec<? extends XiePlantBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(XieAges.pending());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (shapesByAge.isEmpty()) {
            return super.getShape(state, level, pos, context);
        }
        int age = age(state);
        VoxelShape shape = age < shapesByAge.size() ? shapesByAge.get(age) : null;
        return shape != null ? shape : super.getShape(state, level, pos, context);
    }

    /**
     * {@code growsOn}, plus this block itself when it stacks.
     *
     * <p>The original drew that distinction twice: {@code canBePlantedOn} rejected
     * the block's own id but {@code canGrowOn} accepted it, so a corn stalk could
     * grow a second segment on top of itself but a player could not place one
     * there. Modern placement and survival both run through {@code canSurvive},
     * so the lenient rule wins — the alternative is segments popping off the
     * moment the chunk reloads.
     */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        if (config.isTall() && state.is(this)) {
            return true;
        }
        if (config.growsOn().isEmpty()) {
            return super.mayPlaceOn(state, level, pos);
        }
        return config.growsOn().contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    /** A ripe plant with nothing left to grow into has nothing left to do. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !isRipe(state) || growsWhenRipe();
    }

    /**
     * Whether reaching the last growth stage is the end of it.
     *
     * <p>Two archetypes say no. A plant with a {@code growthHeight} puts a new
     * segment on top; a {@link XieSaplingBlock} replaces itself with a tree. Both
     * ran through {@code XieBlockPlant.tryToGrowMoah}, which is where the original
     * asked the same question.
     */
    protected boolean growsWhenRipe() {
        return config.isTall();
    }

    /**
     * One growth roll per random tick, as {@code XieBlockPlant.updateTick} did:
     * roll first, then either advance a stage or, when already ripe, try to put a
     * new segment on top.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) < config.growthLight()) {
            return;
        }
        if (random.nextInt(growthDenominator(state, level, pos)) != 0) {
            return;
        }
        if (!isRipe(state)) {
            level.setBlock(pos, state.setValue(ageProperty, age(state) + 1), Block.UPDATE_CLIENTS);
        } else if (growsWhenRipe()) {
            growWhenRipe(level, pos, state, random);
        }
    }

    /**
     * What a ripe block does with a growth roll it has no stages left to spend.
     * {@code XieBlockPlant.tryToGrowMoah}: a tall plant grows a segment, and a
     * sapling turns into whatever its {@code growsInto} names.
     */
    protected void growWhenRipe(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        growTaller(level, pos);
    }

    /**
     * Whether {@link #growWhenRipe} has anywhere to go: air above and a column not
     * yet {@code growthHeight} tall. Asked by bone meal, so the top of a full stalk
     * refuses it and the stack is kept, as vanilla's ripe wheat does.
     */
    protected boolean hasRoomWhenRipe(LevelReader level, BlockPos pos) {
        return level.isEmptyBlock(pos.above()) && columnHeight(level, pos) < config.growthHeight();
    }

    /**
     * The denominator of the per-tick growth roll: a 1-in-<var>n</var> chance.
     *
     * <p>{@code growthRate} is a percentage where 100 is vanilla's default speed,
     * so a higher rate divides the denominator down and the plant grows more
     * often. {@link XieCropBlock} divides it again by the farmland bonus.
     */
    protected int growthDenominator(BlockState state, ServerLevel level, BlockPos pos) {
        return Math.max(1, GROWTH_DENOMINATOR * 100 / config.growthRate());
    }

    /**
     * Puts a fresh segment on top, from {@code XieBlockPlant.attemptToGrowHigher}.
     * The new segment starts at age 0 and grows through its own stages.
     */
    private void growTaller(ServerLevel level, BlockPos pos) {
        if (hasRoomWhenRipe(level, pos)) {
            level.setBlockAndUpdate(pos.above(), defaultBlockState());
        }
    }

    /**
     * How many of this block are stacked at and below {@code pos}, counting from 1.
     * Bounded by {@code growthHeight} because that is the only question being asked
     * of it.
     */
    private int columnHeight(BlockGetter level, BlockPos pos) {
        int height = 0;
        while (height < config.growthHeight() && level.getBlockState(pos.below(height)).is(this)) {
            height++;
        }
        return height;
    }

    // -------------------------------------------------------------- bone meal

    /**
     * Bone meal, from {@code XieBlockPlant.fertilize}: two to five stages at once,
     * and any growth left over after ripening goes into a new segment on top.
     */
    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        if (!config.fertilisable()) {
            return false;
        }
        return !isRipe(state) || (growsWhenRipe() && hasRoomWhenRipe(level, pos));
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (isRipe(state)) {
            growWhenRipe(level, pos, state, random);
            return;
        }
        int grown = age(state) + Mth.nextInt(random, 2, 5);
        BlockState ripened = state.setValue(ageProperty, Math.min(grown, config.maxAge()));
        level.setBlock(pos, ripened, Block.UPDATE_CLIENTS);
        if (grown > config.maxAge() && growsWhenRipe()) {
            growWhenRipe(level, pos, ripened, random);
        }
    }

    /** {@code spikey}. See {@link XieSpikes}. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effects, boolean isPrecise) {
        XieSpikes.hurt(config.spikey(), level, entity);
    }
}
