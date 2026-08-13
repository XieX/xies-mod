package mod.xie.xm.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * The {@code sapling} archetype: a plant that replaces itself with a tree.
 *
 * <p>It really is just a plant. {@code XieBlockSapling} extended
 * {@code XieBlockPlant} and changed one thing — what happens once it is fully
 * grown — and so does this. Everything else, from where it may be planted to the
 * growth roll itself, is {@link XiePlantBlock}'s.
 *
 * <p>Two details are worth naming, because both come from the original having been
 * vanilla's sapling with the serial numbers filed off:
 *
 * <ul>
 *   <li>{@code XieBlockSapling} set {@code growthRate} to 14, which its formula
 *       turned into a 1-in-7 roll — exactly {@code SaplingBlock.randomTick}. It also
 *       spent a metadata bit on a single intermediate stage, which is exactly
 *       vanilla's {@code stage}. So a Xie sapling was a vanilla sapling, and
 *       {@link #SAPLING_GROWTH_DENOMINATOR} keeps it one.</li>
 *   <li>The tree it becomes is a configured feature, not code. See
 *       {@link XieTreeBlock}.</li>
 * </ul>
 */
public class XieSaplingBlock extends XiePlantBlock {

    /**
     * The growth roll a sapling gets at the default {@code growthRate}: 1 in 7 per
     * random tick, which is {@code SaplingBlock}'s and the original's alike.
     *
     * <p>{@link XiePlantBlock#GROWTH_DENOMINATOR} is the same constant for the
     * archetypes that grow through stages, where vanilla's baseline is a crop's.
     * A sapling's baseline is a sapling's.
     */
    public static final int SAPLING_GROWTH_DENOMINATOR = 7;

    public static final MapCodec<XieSaplingBlock> CODEC =
        simpleCodec(properties -> new XieSaplingBlock(properties, Config.PLACEHOLDER, Optional.empty()));

    /** Absent when {@code growsInto} names a block this build could not register. */
    private final Optional<TreeGrower> grower;

    public XieSaplingBlock(BlockBehaviour.Properties properties, Config config,
                           Optional<TreeGrower> grower) {
        super(properties, config);
        this.grower = grower;
    }

    @Override
    protected MapCodec<? extends XieSaplingBlock> codec() {
        return CODEC;
    }

    @Override
    protected int growthDenominator(BlockState state, ServerLevel level, BlockPos pos) {
        return Math.max(1, SAPLING_GROWTH_DENOMINATOR * 100 / config().growthRate());
    }

    /** Reaching the last stage is where a sapling's real work starts. */
    @Override
    protected boolean growsWhenRipe() {
        return grower.isPresent();
    }

    /**
     * Grows the tree, from {@code XieBlockPlant.growIntoSomethingElse}.
     *
     * <p>{@link TreeGrower#growTree} is that method: it clears the sapling, places
     * the configured feature, and puts the sapling back when the feature refuses —
     * which is what happens when there is not enough room. Handing the work to it
     * rather than reimplementing those three steps also puts the growth through
     * NeoForge's {@code BlockGrowFeatureEvent}, so another mod can substitute or
     * veto the tree without knowing this one exists.
     */
    @Override
    protected void growWhenRipe(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        grower.ifPresent(tree -> {
            if (tree.growTree(level, level.getChunkSource().getGenerator(), pos, state, random)) {
                // The feature has no idea this tree fruits; only a planted one does.
                XieTreeBlock.markKeystone(level, pos);
            }
        });
    }
}
