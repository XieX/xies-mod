package mod.xie.xm.block;

import com.mojang.serialization.MapCodec;

import mod.xie.xm.content.model.Bounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

/**
 * The {@code tree} archetype: the leaves of a fruit tree.
 *
 * <p>Almost everything the original {@code XieBlockLeaves} did is now somebody
 * else's job, and that is the point of the archetype being this small:
 *
 * <ul>
 *   <li><b>The tree itself</b> is a datapack {@code minecraft:tree} feature rather
 *       than {@code TreeGenerator}. The three generators that file held were 1.6's
 *       own oak, spruce and pine copied out of vanilla, and all three are still in
 *       the game as configurations of that feature — so the shape is emitted as
 *       JSON and vanilla places it, for worldgen and for a growing sapling alike.
 *       See {@code XieWorldgenEmitter} and {@link XieSaplingBlock}.</li>
 *   <li><b>Leaf decay</b> is {@link LeavesBlock}'s. The forty lines of flood fill in
 *       {@code XieBlockLeaves.updateTick} were 1.6's {@code BlockLeaves} verbatim;
 *       the modern one keeps a {@code distance} property instead and arrives at the
 *       same answer without the array.</li>
 *   <li><b>Drops</b> are the generated loot table, as every other archetype's are.</li>
 * </ul>
 *
 * <p>What is left is the fruit. In 2013 one leaf per tree — the keystone, directly
 * above the trunk — rolled on every random tick to drop an item on the ground
 * beside the tree, and stopped once eight were lying there. That was the one thing
 * vanilla had no equivalent for, and it still has none; but it has since reached a
 * different design for the same need twice, and this block follows it instead.
 * A sweet berry bush and a cave vine both grow their fruit <em>on the block</em>,
 * show it, and hand it over on a right-click that leaves the block standing. So a
 * leaf carries an {@code age}, ripens on its own random ticks when it is on the
 * outside of the canopy, and is picked like a berry bush. The fruit itself is a
 * harvest loot table, as vanilla's is.
 *
 * <p>Random ticks are sampled per chunk section rather than scheduled per block, so
 * fifty leaves that may ripen cost the server nothing fifty keystones would not have,
 * and the item entities and the entity scan that saturation needed are gone with it.
 * What a canopy of ripening leaves does cost is yield — fifty leaves is fifty times
 * one keystone — which is what {@code growthRate} is for.
 */
public class XieTreeBlock extends LeavesBlock implements BonemealableBlock {

    /**
     * @param maxAge       the ripe stage, or zero for a tree that bears nothing;
     *                     {@code maxGrowth}, which a fruit tree leaves at one
     * @param growthRate   percentage of the baseline ripening speed; {@code growthRate}
     * @param fertilisable whether bone meal ripens a leaf; {@code fertilisable}
     * @param spikey       whether standing in the canopy hurts; see {@link XieSpikes}.
     *                     {@code blazeTree} is the one definition in the content that
     *                     asks for it, and the only one on any archetype
     * @param shape        the canopy's box, absent for a full block. Read here rather
     *                     than left to the model, because {@code blazeTree} is drawn as a
     *                     cross ({@code renderType: "flower"}) and a cross takes its
     *                     shape from no box at all — which is the same split vanilla
     *                     wheat has, where the model is flat quads and the box is only
     *                     what you collide with and outline
     */
    public record Config(int maxAge, int growthRate, boolean fertilisable, boolean spikey,
                         Optional<Bounds> shape) {

        /** Used only to satisfy the block codec; never placed in a world. */
        public static final Config PLACEHOLDER =
            new Config(0, 100, false, false, Optional.empty());

        public boolean bearsFruit() {
            return maxAge > 0;
        }
    }

    /**
     * Where a tree's harvest loot table lives, under the mod's namespace and ahead of
     * the block's own path: {@code xiesmod:harvest/apple_leaves}. Vanilla keeps its
     * picked things at the same kind of address, {@code minecraft:harvest/cave_vine},
     * and {@code XieDataEmitter} writes one per fruiting subtype.
     */
    public static final String HARVEST_PREFIX = "harvest/";

    /** Leaves are not a Mojang particle source here; the 2013 block had none. */
    private static final float NO_LEAF_PARTICLES = 0f;

    /** {@code Xie.setFlammability(leafID, 30, 60)}, which is vanilla's for leaves. */
    private static final int LEAF_IGNITE_ODDS = 30;
    private static final int LEAF_BURN_ODDS = 60;

    public static final MapCodec<XieTreeBlock> CODEC =
        simpleCodec(properties -> new XieTreeBlock(properties, Config.PLACEHOLDER));

    private final Config config;

    /** Null for a tree that bears nothing, which then has no {@code age} at all. */
    private final IntegerProperty ageProperty;

    /** The declared box, or null for a plain full block. */
    private final VoxelShape shape;

    /** Resolved from the registry on first pick; the block has no name until then. */
    private volatile ResourceKey<LootTable> harvestTable;

    /** The age property is built before this constructor's body runs; see {@link XieAges}. */
    public XieTreeBlock(BlockBehaviour.Properties properties, Config config) {
        super(NO_LEAF_PARTICLES, XieAges.primeOptional(properties, config.maxAge()));
        XieAges.clear();

        this.config = config;
        this.ageProperty = config.bearsFruit() ? XieAges.property(config.maxAge()) : null;
        this.shape = config.shape().map(box -> {
            float[] px = box.toPixels();
            return Block.box(px[0], px[1], px[2], px[3], px[4], px[5]);
        }).orElse(null);
        if (ageProperty != null) {
            registerDefaultState(defaultBlockState().setValue(ageProperty, 0));
        }
    }

    /**
     * {@code blockBounds}, which {@code blazeTree} uses to inset its canopy.
     *
     * <p>{@code expandCommonBlockParameters} read it for every archetype, leaves
     * included, and {@code Block.setBlockBounds} was both the collision box and the
     * selection outline. On a cube block it was also the drawing, which the pack
     * emitter writes out as a model element — but a cross has no box in its model, so
     * for a canopy this is the whole of what the property still means.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return shape != null ? shape : super.getShape(state, level, pos, context);
    }

    public Config config() {
        return config;
    }

    @Override
    public MapCodec<? extends XieTreeBlock> codec() {
        return CODEC;
    }

    /**
     * {@code distance}, {@code persistent} and {@code waterlogged} from
     * {@link LeavesBlock}, plus {@code age} for a tree that bears fruit. A tree that
     * does not gets no property rather than one stuck at zero, so {@code blazeTree}'s
     * blockstate is exactly a vanilla leaf's.
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        XieAges.pendingOptional().ifPresent(builder::add);
    }

    /**
     * The 2013 block drew no falling-leaf particles, because they did not exist.
     * With {@link #NO_LEAF_PARTICLES} as the chance {@code LeavesBlock} never asks,
     * so this is unreachable rather than empty by choice.
     */
    @Override
    protected void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random) {
        // Nothing to draw.
    }

    /**
     * {@code Xie.setFlammability(leafID, 30, 60)} — the numbers vanilla still gives
     * its own leaves. NeoForge asks the block rather than keeping a table, so this
     * is where they go now.
     */
    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction from) {
        return LEAF_IGNITE_ODDS;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction from) {
        return LEAF_BURN_ODDS;
    }

    // ------------------------------------------------------------------ fruit

    public boolean isRipe(BlockState state) {
        return ageProperty != null && state.getValue(ageProperty) >= config.maxAge();
    }

    /**
     * Whether this leaf has a stage left to ripen through.
     *
     * <p>A {@code persistent} leaf is one a player placed, and it bears nothing: a
     * hedge of sheared leaves is not an orchard, and vanilla draws the same line when
     * it refuses to decay them. The tree feature places leaves non-persistent whether
     * the world or a sapling grew it, so every tree bears.
     */
    private boolean canRipen(BlockState state) {
        return ageProperty != null
            && !state.getValue(PERSISTENT)
            && state.getValue(ageProperty) < config.maxAge();
    }

    /**
     * A leaf ticks when it may decay, as vanilla's does, or when it may ripen.
     *
     * <p>Asking is free: the server samples a few random positions per chunk section
     * per tick and consults the block it finds there, so the number of leaves that
     * answer yes changes what a tick does, not how many ticks there are.
     */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return super.isRandomlyTicking(state) || canRipen(state);
    }

    /**
     * Decay first, as vanilla's tick is nothing but; a leaf that is still standing
     * afterwards may ripen instead. The roll is a crop's, {@code 1 in 25} at the
     * default {@code growthRate}, and {@code growthRate} means the same thing here as
     * on every other archetype that ages.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (decaying(state)) {
            super.randomTick(state, level, pos, random);
            return;
        }
        if (canRipen(state) && mayRipenAt(level, pos)
            && random.nextInt(ripeningDenominator()) == 0) {
            ripen(level, pos, state, state.getValue(ageProperty) + 1);
        }
    }

    private int ripeningDenominator() {
        return Math.max(1, XiePlantBlock.GROWTH_DENOMINATOR * 100 / config.growthRate());
    }

    /**
     * Whether this leaf may ripen where it stands: a face open to the air, and no
     * ripe leaf beside it that is open to the air too.
     *
     * <p>The first is the outside of the canopy, its underside included, which is the
     * face a player on the ground can reach; fruit on a leaf buried in the middle
     * could be neither seen nor picked, so it is never grown. The second spaces the
     * fruit out and caps what a tree nobody picks holds, at about a third of its
     * outside; a leaf is eligible again the moment the one beside it is picked, so a
     * tended tree loses nothing. Vanilla's mushrooms cap their spread the same way,
     * by a count of their own kind over a nine-by-three-by-nine box; six neighbours
     * is that rule at a fraction of the reads, and they are the reads the exposure
     * test makes anyway. A ripe leaf that is itself buried does not count: the tree
     * feature places ripe leaves blind, and a buried one would otherwise hold its
     * side of the canopy bare for good.
     */
    private static boolean mayRipenAt(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos beside = new BlockPos.MutableBlockPos();
        boolean exposed = false;
        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(beside.setWithOffset(pos, direction));
            if (neighbour.isAir()) {
                exposed = true;
            } else if (ripe(neighbour) && isExposed(level, beside)) {
                return false;
            }
        }
        return exposed;
    }

    private static boolean ripe(BlockState state) {
        return state.getBlock() instanceof XieTreeBlock tree && tree.isRipe(state);
    }

    private static boolean isExposed(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos beside = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(beside.setWithOffset(pos, direction)).isAir()) {
                return true;
            }
        }
        return false;
    }

    private void ripen(ServerLevel level, BlockPos pos, BlockState state, int age) {
        BlockState ripened = state.setValue(ageProperty, Math.min(age, config.maxAge()));
        level.setBlock(pos, ripened, Block.UPDATE_CLIENTS);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(ripened));
    }

    /**
     * The harvest: a ripe leaf hands over its fruit on a right-click and goes back to
     * bare, exactly as {@code SweetBerryBushBlock.useWithoutItem} and
     * {@code CaveVines.use} do, down to the sound.
     *
     * <p>Not a punch. A left-click is the start of mining, a bare hand breaks a leaf
     * in about a third of a second and creative breaks it at once, so the leaf being
     * shaken would as often be the leaf being destroyed. The right-click is also what
     * {@link XieBushBlock} settled on, for the same reason.
     *
     * <p>The fruit comes out of the loot table at {@link #HARVEST_PREFIX} rather than
     * a field of this block, so what a tree bears is written in the datapack where
     * every other drop is and nothing here has to resolve an item. It pops out of the
     * face that was clicked: a berry bush is hollow and drops its berries where they
     * are, but a leaf is a full block, and an item spawned inside one has to be pushed
     * out of it first.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!isRipe(state)) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }

        if (level instanceof ServerLevel serverLevel) {
            Block.dropFromBlockInteractLootTable(serverLevel, harvestTable(), state, null, null,
                player, (where, stack) -> Block.popResourceFromFace(where, pos,
                    hitResult.getDirection(), stack));

            serverLevel.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                SoundSource.BLOCKS, 1.0F, 0.8F + serverLevel.getRandom().nextFloat() * 0.4F);

            BlockState picked = state.setValue(ageProperty, 0);
            serverLevel.setBlock(pos, picked, Block.UPDATE_CLIENTS);
            serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, picked));
        }
        return InteractionResult.SUCCESS;
    }

    private ResourceKey<LootTable> harvestTable() {
        ResourceKey<LootTable> key = harvestTable;
        if (key == null) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(this);
            harvestTable = key = ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(id.getNamespace(), HARVEST_PREFIX + id.getPath()));
        }
        return key;
    }

    // --------------------------------------------------------------- bone meal

    /** Bone meal ripens a leaf one stage, as it puts the berries on a cave vine. */
    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return config.fertilisable() && canRipen(state);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        ripen(level, pos, state, state.getValue(ageProperty) + 1);
    }

    /** {@code spikey}. See {@link XieSpikes}. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effects, boolean isPrecise) {
        XieSpikes.hurt(config.spikey(), level, entity);
    }
}
