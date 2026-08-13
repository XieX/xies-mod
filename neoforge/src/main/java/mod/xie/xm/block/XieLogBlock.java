package mod.xie.xm.block;

import com.mojang.serialization.MapCodec;

import mod.xie.xm.content.model.Bounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.Optional;

/**
 * The {@code log} archetype: a trunk block.
 *
 * <p><strong>{@code XieBlockLog} was 1.6's own {@code BlockLog} with a name on it.</strong>
 * Three hundred lines, and the class body proper is seven of them — hardness 2.0,
 * flammability 5/5, effective against every axe, {@code canSustainLeaves} and
 * {@code isWood} both true. Every one of those is what a modern log already is:
 * {@code RotatedPillarBlock} with vanilla's log strength, {@code #minecraft:logs}
 * and {@code #minecraft:mineable/axe}. The remaining two hundred and fifty lines are
 * marked {@code // Copy=pasta from XieBlock} in the original and are the same generic
 * block boilerplate every other archetype carried.
 *
 * <p>That is the fourth time this port has found a 2013 class to be vanilla's — after
 * {@code TreeGenerator}'s three tree generators, {@code XieBlockLeaves.updateTick} and
 * {@code XieEntityArrow} — so what is left here is only the two pieces of that
 * boilerplate a log can still be asked for:
 *
 * <ul>
 *   <li>{@code blockBounds}, which {@code blazeLog} uses to make a slim four-pixel
 *       pillar rather than a full trunk. It rotates with the block's axis, which the
 *       original's fixed {@code AxisAlignedBB} did not — 1.6 stored the axis in the
 *       same metadata it read nothing else from, and a tree only ever places an
 *       upright trunk, so no log in the content was ever drawn sideways for the
 *       difference to show.</li>
 *   <li>{@code becomesWhenDestroyed}, which no log in the content declares and which
 *       costs one field to keep rather than lose in silence. See {@link XieBecomes}.</li>
 * </ul>
 */
public class XieLogBlock extends RotatedPillarBlock {

    /**
     * @param becomesWhenDestroyed namespaced id of the block left behind, if any
     * @param shape                the trunk's box, absent for a full block
     * @param spikey               whether standing in it hurts; see {@link XieSpikes}
     */
    public record Config(Optional<String> becomesWhenDestroyed, Optional<Bounds> shape,
                         boolean spikey) {

        /** Used only to satisfy the block codec; never placed in a world. */
        public static final Config PLACEHOLDER =
            new Config(Optional.empty(), Optional.empty(), false);
    }

    public static final MapCodec<XieLogBlock> CODEC =
        simpleCodec(properties -> new XieLogBlock(properties, Config.PLACEHOLDER));

    private final Config config;
    private final XieBecomes becomes;

    /** The declared box turned about each axis, or null for a plain full block. */
    private final Map<Direction.Axis, VoxelShape> shapes;

    public XieLogBlock(BlockBehaviour.Properties properties, Config config) {
        super(properties);
        this.config = config;
        this.becomes = new XieBecomes(config.becomesWhenDestroyed());
        this.shapes = config.shape().map(XieLogBlock::shapesFor).orElse(null);
    }

    public Config config() {
        return config;
    }

    @Override
    public MapCodec<? extends XieLogBlock> codec() {
        return CODEC;
    }

    /**
     * {@code Xie.setFlammability(logID, 5, 5)} — the numbers vanilla still gives its
     * own logs, and the one thing {@code #minecraft:logs} does not carry:
     * {@code FireBlock.bootStrap} names each log block outright rather than reading
     * the tag. NeoForge asks the block instead of keeping a table, so this is where
     * they go, exactly as {@link XieTreeBlock} puts its leaves' 30/60.
     */
    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction from) {
        return IGNITE_ODDS;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction from) {
        return BURN_ODDS;
    }

    private static final int IGNITE_ODDS = 5;
    private static final int BURN_ODDS = 5;

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return shapes != null
            ? shapes.get(state.getValue(AXIS))
            : super.getShape(state, level, pos, context);
    }

    /** Leaves {@code becomesWhenDestroyed} behind. See {@link XieBecomes}. */
    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        becomes.applyOnDestroy(level, pos);
    }

    /** {@code spikey}. See {@link XieSpikes}. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effects, boolean isPrecise) {
        XieSpikes.hurt(config.spikey(), level, entity);
    }

    /**
     * The declared box as written, and the same box laid on its side each way.
     *
     * <p>{@code blockBounds} describes an upright trunk, because that is the only
     * orientation a definition can describe — 1.6 had one AABB per block and no way
     * to say "when it lies east to west". Turning it is a permutation of the same six
     * numbers, so a slim pillar stays slim whichever way the trunk runs.
     */
    private static Map<Direction.Axis, VoxelShape> shapesFor(Bounds box) {
        float[] px = box.toPixels();
        return Map.of(
            Direction.Axis.Y, Block.box(px[0], px[1], px[2], px[3], px[4], px[5]),
            Direction.Axis.X, Block.box(px[1], px[0], px[2], px[4], px[3], px[5]),
            Direction.Axis.Z, Block.box(px[0], px[2], px[1], px[3], px[5], px[4]));
    }
}
