package mod.xie.xm.block;

import mod.xie.xm.content.model.Bounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;
import java.util.Set;

/**
 * The {@code basic} archetype: a block that does nothing but sit there.
 *
 * <p>It is the default archetype — a definition with no {@code type} is basic — and
 * in the original it was {@code XieBlock}, a thin subclass of {@code Block}. Three
 * properties give it what character it has:
 *
 * <ul>
 *   <li>{@code becomesWhenDestroyed} — the block left behind when a player breaks
 *       it, so that scraping something off the ground leaves the ground rather
 *       than a hole.</li>
 *   <li>{@code walkthroughable} — no collision, applied as {@code noCollision()}
 *       when the properties are built; see {@code XieBlockProperties.forBasic}.</li>
 *   <li>{@code textureBase} — inherit a vanilla block's texture and override
 *       single faces. That one is entirely a question for the pack emitter; by the
 *       time a block exists the textures have already been written.</li>
 * </ul>
 *
 * <p>Basic blocks have no age and no growth, so unlike {@link XiePlantBlock} they
 * have a single blockstate and the emitter writes them a plain model.
 */
public class XieBasicBlock extends Block {

    /**
     * @param becomesWhenDestroyed namespaced id of the block left behind, if any
     * @param shape                collision/selection box, absent for a full cube
     * @param needsSupport         whether it falls apart when what it stands on goes
     * @param growsOn              namespaced ids it may stand on; empty means the
     *                             vanilla rule for vegetation
     * @param spikey               whether standing in it hurts; see {@link XieSpikes}
     */
    public record Config(Optional<String> becomesWhenDestroyed, Optional<Bounds> shape,
                         boolean needsSupport, Set<String> growsOn, boolean spikey) {

        public Config {
            growsOn = Set.copyOf(growsOn);
        }
    }

    private final Config config;
    private final VoxelShape shape;
    private final XieBecomes becomes;

    public XieBasicBlock(BlockBehaviour.Properties properties, Config config) {
        super(properties);
        this.config = config;
        this.becomes = new XieBecomes(config.becomesWhenDestroyed());
        this.shape = config.shape().map(box -> {
            float[] px = box.toPixels();
            return Block.box(px[0], px[1], px[2], px[3], px[4], px[5]);
        }).orElse(null);
    }

    public Config config() {
        return config;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape != null ? shape : super.getShape(state, level, pos, context);
    }

    /**
     * A walkthroughable basic block is vegetation, and vegetation falls when what it
     * stands on is taken away.
     *
     * <p>This is a deliberate deviation. {@code XieBlock} was a plain {@code Block}
     * and 1.6 only ever checked support for {@code BlockBush} and its descendants, so
     * a shrub whose dirt you mined stayed floating in 2013 too. It is faithful and it
     * looks broken, which is the same trade the growth-rate fix made: every vanilla
     * plant since Alpha has come away with its soil, and a cross-model block hanging
     * in mid-air reads as a bug no matter what the original did.
     *
     * <p>Only {@code walkthroughable} definitions are affected, which is what tells a
     * shrub from a {@code saltBlock} — one is something you walk through and the
     * other is terrain. {@code growsOn} narrows it further if a definition says so,
     * exactly as it does on {@link XiePlantBlock}, and is how a salt layer stands on
     * sand; with none declared this falls back to the rule vanilla uses for grass
     * and flowers.
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!config.needsSupport()) {
            return super.canSurvive(state, level, pos);
        }
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);

        if (!config.growsOn().isEmpty()) {
            return config.growsOn()
                .contains(BuiltInRegistries.BLOCK.getKey(belowState.getBlock()).toString());
        }
        // NeoForge lets a block answer for itself before the tag is consulted, which
        // is how other mods' soils work without either side knowing about the other.
        var soil = belowState.canSustainPlant(level, below, Direction.UP, state);
        return soil.isDefault() ? belowState.is(BlockTags.SUPPORTS_VEGETATION) : soil.isTrue();
    }

    /**
     * Turning to air here is what makes it drop rather than vanish: the caller is
     * {@code Block.updateOrDestroy}, which routes an air result through
     * {@code destroyBlock} and so runs the loot table. It is how vanilla grass comes
     * away with its dirt, and it means a wild corn plant still yields its cob.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                     BlockPos pos, Direction directionToNeighbour,
                                     BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        if (config.needsSupport() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour,
            neighbourPos, neighbourState, random);
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
}
