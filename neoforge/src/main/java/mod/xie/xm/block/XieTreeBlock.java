package mod.xie.xm.block;

import com.mojang.serialization.MapCodec;

import mod.xie.xm.content.model.Bounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
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
 * <p>What is left is the thing vanilla has no equivalent for, thirteen years on:
 * <b>a tree that drops its fruit on the ground</b>. That is {@code sheds}, and it is
 * why this is a block and not just three JSON files.
 */
public class XieTreeBlock extends LeavesBlock {

    /**
     * One entry of a {@code sheds} table.
     *
     * @param itemId namespaced id of what falls
     * @param chance percentage chance per shed attempt
     * @param amount independent rolls per attempt, as {@code drops} amounts are
     */
    public record Shed(String itemId, int chance, int amount) { }

    /**
     * @param sheds      what this tree lets fall, already narrowed to this subtype
     * @param saturation how many of the shed items may be lying nearby before the
     *                   tree stops; {@code shedSaturation}, zero for no limit
     * @param spikey     whether standing in the canopy hurts; see {@link XieSpikes}.
     *                   {@code blazeTree} is the one definition in the content that
     *                   asks for it, and the only one on any archetype
     * @param shape      the canopy's box, absent for a full block. Read here rather
     *                   than left to the model, because {@code blazeTree} is drawn as a
     *                   cross ({@code renderType: "flower"}) and a cross takes its
     *                   shape from no box at all — which is the same split vanilla
     *                   wheat has, where the model is flat quads and the box is only
     *                   what you collide with and outline
     */
    public record Config(List<Shed> sheds, int saturation, boolean spikey,
                         Optional<Bounds> shape) {

        public Config {
            sheds = List.copyOf(sheds);
        }

        /** Used only to satisfy the block codec; never placed in a world. */
        public static final Config PLACEHOLDER =
            new Config(List.of(), 0, false, Optional.empty());

        public boolean shedsAnything() {
            return !sheds.isEmpty();
        }
    }

    /**
     * The one leaf per tree that fruit falls from: the one directly above the
     * topmost trunk log.
     *
     * <p>{@code XieBlockLeaves} called it the keystone and packed it into bit 2 of
     * the block's metadata, next to the two bits the subtype used and the one leaf
     * decay used. There is no metadata to pack any more, so it is a property of its
     * own — but the reason for having exactly one shedding block per tree is the
     * same one it always was, and it is a good one: a canopy is fifty blocks, and
     * fifty leaves each rolling for fruit is fifty times the fruit and fifty times
     * the work per random tick.
     *
     * <p>Every tree block carries the property whether or not it sheds. A tree with
     * no {@code sheds} table simply never has it set, which costs it one unused
     * blockstate and saves the archetype a conditional state definition.
     */
    public static final BooleanProperty KEYSTONE = BooleanProperty.create("keystone");

    /** The eight horizontal neighbours fruit may fall beside, {@code XieBlockLeaves.ADJ}. */
    private static final int[][] ADJACENT =
        {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

    /** How far fruit looks down for ground before giving up. */
    private static final int MAX_FALL = 16;

    /** The radius {@code Xie.entityCount} counted lying fruit within. */
    private static final int SATURATION_RADIUS = 4;

    /** How far above a sapling a trunk may reach, for {@link #markKeystone}. */
    private static final int MAX_TRUNK_HEIGHT = 32;

    /** Leaves are not a Mojang particle source here; the 2013 block had none. */
    private static final float NO_LEAF_PARTICLES = 0f;

    /** {@code Xie.setFlammability(leafID, 30, 60)}, which is vanilla's for leaves. */
    private static final int LEAF_IGNITE_ODDS = 30;
    private static final int LEAF_BURN_ODDS = 60;

    public static final MapCodec<XieTreeBlock> CODEC =
        simpleCodec(properties -> new XieTreeBlock(properties, Config.PLACEHOLDER));

    private final Config config;

    /**
     * Resolved on first shed rather than in the constructor: leaves are created
     * while the block registry is still filling, and a tree may shed an item this
     * mod has not registered yet — or, with a third-party pack, at all.
     */
    private volatile List<ResolvedShed> resolved;

    private record ResolvedShed(Item item, int chance, int amount) { }

    /** The declared box, or null for a plain full block. */
    private final VoxelShape shape;

    public XieTreeBlock(BlockBehaviour.Properties properties, Config config) {
        super(NO_LEAF_PARTICLES, properties);
        this.config = config;
        this.shape = config.shape().map(box -> {
            float[] px = box.toPixels();
            return Block.box(px[0], px[1], px[2], px[3], px[4], px[5]);
        }).orElse(null);
        registerDefaultState(defaultBlockState().setValue(KEYSTONE, false));
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

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(KEYSTONE);
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

    /** A keystone ticks even when it is nowhere near decaying, because it fruits. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return super.isRandomlyTicking(state) || isKeystone(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (config.shedsAnything() && isKeystone(state)) {
            shed(level, pos, random);
        }
        super.randomTick(state, level, pos, random);
    }

    private static boolean isKeystone(BlockState state) {
        return state.hasProperty(KEYSTONE) && state.getValue(KEYSTONE);
    }

    /**
     * Marks the leaf a freshly grown tree should fruit from: the first block above
     * the trunk that rises from {@code base}.
     *
     * <p>{@code TreeGenerator} wrote the keystone straight into position
     * {@code j + trunkHeight}, which is one above the topmost log and inside the
     * canopy every one of its three shapes puts there. Walking the trunk finds the
     * same block without the generator having to hand the height over, which is what
     * lets the shape come from a datapack.
     *
     * <p>Called only when a sapling grows. Trees that generate with the world do not
     * shed, exactly as in 2013, where {@code Spawnable} passed {@code natural} and
     * the keystone was skipped for it — a forest of wild fruit trees quietly dropping
     * fruit is a lot of item entities for something nobody planted.
     */
    public static void markKeystone(ServerLevel level, BlockPos base) {
        BlockPos.MutableBlockPos cursor = base.mutable();
        for (int height = 0; height < MAX_TRUNK_HEIGHT; height++) {
            if (!level.getBlockState(cursor).is(BlockTags.LOGS)) {
                break;
            }
            cursor.move(0, 1, 0);
        }

        BlockPos above = cursor.immutable();
        BlockState leaf = level.getBlockState(above);
        if (leaf.getBlock() instanceof XieTreeBlock tree && tree.config().shedsAnything()
            && !isKeystone(leaf)) {
            level.setBlock(above, leaf.setValue(KEYSTONE, true), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ sheds

    /**
     * Drops fruit beside the tree, from {@code XieBlockLeaves.shed}: pick one of the
     * eight neighbouring columns, follow it down to the ground, and leave the fruit
     * there.
     *
     * <p>The original's descent reads {@code while (blockID != 0)} — it walks down
     * while the column is <em>solid</em> and stops at the first air, which is the
     * opposite of finding the ground. It made little difference in play, since an
     * item spawned inside the canopy falls out of it anyway, but the intent is not
     * in doubt and this is the third formula in that codebase to have needed reading
     * rather than copying. See the growth-rate and {@code spawnsNear} notes in
     * PORTING.md.
     */
    private void shed(ServerLevel level, BlockPos pos, RandomSource random) {
        List<ItemStack> falling = roll(random);
        if (falling.isEmpty()) {
            return;
        }

        BlockPos ground = groundBeside(level, pos, random);
        if (isSaturated(level, ground, falling)) {
            return;
        }
        falling.forEach(stack -> Block.popResource(level, ground, stack));
    }

    /**
     * The first standable spot in one of the eight neighbouring columns, or the
     * tree's own position when the column runs out before the ground does.
     */
    private static BlockPos groundBeside(ServerLevel level, BlockPos pos, RandomSource random) {
        int[] direction = ADJACENT[random.nextInt(ADJACENT.length)];
        BlockPos.MutableBlockPos at = pos.offset(direction[0], 0, direction[1]).mutable();

        for (int fallen = 0; fallen < MAX_FALL; fallen++) {
            if (level.isEmptyBlock(at) && isGround(level.getBlockState(at.below()))) {
                return at.immutable();
            }
            at.move(0, -1, 0);
        }
        return pos;
    }

    /**
     * Whether fruit can lie on this, which is anything except the tree it fell out
     * of.
     *
     * <p>The keystone sits <em>inside</em> the canopy, so a neighbouring column is
     * air above leaves about as often as it is air above the ground, and stopping at
     * the first surface leaves the fruit on the canopy's own shoulder. That reads as
     * harmless — an item spawned in the leaves falls out of them — but it is not,
     * because {@link #isSaturated} is measured where the fruit was <em>put</em>: a
     * tree that keeps aiming at its own leaves counts the fruit lying under it as
     * lying nowhere near, and goes on shedding after the eight that were supposed to
     * stop it.
     *
     * <p>Found by the acceptance run, which is the whole argument for it existing:
     * the tree with nine cocoa beans under it shed a tenth in two runs out of five,
     * and the answer depended on the trunk's height and which of the eight
     * directions came up. See PORTING.md.
     */
    private static boolean isGround(BlockState below) {
        return !below.isAir()
            && !(below.getBlock() instanceof LeavesBlock)
            && !below.is(BlockTags.LOGS);
    }

    /** One roll of the shed table, as {@code Droppables.getDropsForMetaAndMultiplier}. */
    private List<ItemStack> roll(RandomSource random) {
        List<ItemStack> rolled = new ArrayList<>();
        for (ResolvedShed shed : resolved()) {
            for (int roll = 0; roll < shed.amount(); roll++) {
                if (random.nextInt(100) < shed.chance()) {
                    rolled.add(new ItemStack(shed.item()));
                }
            }
        }
        return rolled;
    }

    /**
     * Whether enough of this fruit is already lying around, from
     * {@code shedSaturation} by way of {@code Droppables.dropBlockAsItem}.
     *
     * <p>It is what keeps an orchard from burying itself: a tree fruits until eight
     * of its apples are on the ground nearby and then stops until somebody picks
     * them up or they despawn.
     */
    private boolean isSaturated(ServerLevel level, BlockPos pos, List<ItemStack> falling) {
        if (config.saturation() <= 0) {
            return false;
        }
        AABB around = new AABB(pos).inflate(SATURATION_RADIUS);
        int lying = level.getEntitiesOfClass(ItemEntity.class, around, entity ->
            falling.stream().anyMatch(stack -> entity.getItem().is(stack.getItem()))).size();
        return lying > config.saturation();
    }

    private List<ResolvedShed> resolved() {
        List<ResolvedShed> found = resolved;
        if (found == null) {
            List<ResolvedShed> items = new ArrayList<>(config.sheds().size());
            for (Shed shed : config.sheds()) {
                Optional.ofNullable(Identifier.tryParse(shed.itemId()))
                    .flatMap(BuiltInRegistries.ITEM::getOptional)
                    .ifPresent(item -> items.add(
                        new ResolvedShed(item, shed.chance(), shed.amount())));
            }
            resolved = found = List.copyOf(items);
        }
        return found;
    }

    /** {@code spikey}. See {@link XieSpikes}. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effects, boolean isPrecise) {
        XieSpikes.hurt(config.spikey(), level, entity);
    }
}
