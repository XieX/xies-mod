package mod.xie.xm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The {@code crop} archetype: an annual that grows faster on well-tended farmland.
 *
 * <p>A crop is a {@link XiePlantBlock} in every respect but one. Where a plant or a
 * bush advances at a flat rate, a crop takes vanilla's farmland-adjacency bonus:
 * soil under and around it makes it grow faster, moist soil faster still, and
 * crowding it against its own kind on both axes halves the result. The original
 * copied that calculation out of {@code BlockCrops} into
 * {@code XieBlockCrop.getCropGrowthRate}, and it is copied again here — from the
 * modern {@code CropBlock.getGrowthSpeed}, which is {@code protected static} and so
 * only reachable by extending it.
 *
 * <p>Extending {@code CropBlock} for that one method was the alternative and does
 * not pay: its age property is fixed at {@code AGE_7} and its shape array is sized
 * to match, while {@code hybridWheat} has nine stages, so the property, the state
 * definition, the shapes, the growth roll and the placement rule would all be
 * overridden — and {@code bush} and {@code plant} cannot extend it at all, which
 * would split one archetype family across two hierarchies to save thirty lines.
 *
 * <p>The other half of "annual" is what this class does <em>not</em> do: unlike
 * {@link XieBushBlock} it has no harvest-in-place. Breaking a ripe crop destroys it
 * and you replant from the seed it drops.
 */
public class XieCropBlock extends XiePlantBlock {

    public XieCropBlock(BlockBehaviour.Properties properties, Config config) {
        super(properties, config);
    }

    /**
     * The plant denominator divided by the farmland bonus, so a crop in a tended
     * field grows several times faster than the same crop on dry soil alone.
     *
     * <p>The original multiplied instead:
     *
     * <pre>
     *   int netGrowthRate = (int) (getCropGrowthRate(world,i,j,k) * (growthRate/100F));
     * </pre>
     *
     * <p>which is inverted twice over. {@code getCropGrowthRate} returns a speed
     * where larger is better, but it is used as {@code nextInt(n) == 0}, where
     * larger is slower — so good soil made a crop grow more slowly. And with the
     * content's {@code growthRate} of 20 the product truncates to 0, is clamped up
     * to 1, and the crop advances a stage on every single random tick regardless of
     * where it is planted. Dividing is what both vanilla and the original's own
     * {@code XieBlockPlant.updateTick} meant, and it makes {@code growthRate} mean
     * the same thing in all three archetypes.
     */
    @Override
    protected int growthDenominator(BlockState state, ServerLevel level, BlockPos pos) {
        float speed = growthSpeed(state, level, pos);
        return Math.max(1, Math.round(super.growthDenominator(state, level, pos) / speed));
    }

    /**
     * Vanilla's {@code CropBlock.getGrowthSpeed}, reproduced.
     *
     * <p>Starts at 1, adds 1 for each of the nine soil blocks beneath the 3×3 around
     * the crop — 3 if that soil is moist, and quartered for the eight that are not
     * directly below — then halves the total if the crop is flanked by its own kind
     * on both axes or on a diagonal.
     */
    private static float growthSpeed(BlockState state, BlockGetter level, BlockPos pos) {
        float speed = 1.0F;
        BlockPos below = pos.below();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos soilPos = below.offset(dx, 0, dz);
                BlockState soil = level.getBlockState(soilPos);
                float contribution = 0.0F;

                // canSustainPlant is NeoForge's hook for modded soils; it answers
                // "default" for everything vanilla, where the tag is the authority.
                var decision = soil.canSustainPlant(level, soilPos, Direction.UP, state);
                if (decision.isDefault() ? soil.is(BlockTags.GROWS_CROPS) : decision.isTrue()) {
                    contribution = soil.isFertile(level, soilPos) ? 3.0F : 1.0F;
                }
                if (dx != 0 || dz != 0) {
                    contribution /= 4.0F;
                }
                speed += contribution;
            }
        }

        Block self = state.getBlock();
        boolean eastWest = level.getBlockState(pos.west()).is(self)
            || level.getBlockState(pos.east()).is(self);
        boolean northSouth = level.getBlockState(pos.north()).is(self)
            || level.getBlockState(pos.south()).is(self);
        boolean diagonal = level.getBlockState(pos.west().north()).is(self)
            || level.getBlockState(pos.east().north()).is(self)
            || level.getBlockState(pos.east().south()).is(self)
            || level.getBlockState(pos.west().south()).is(self);

        if ((eastWest && northSouth) || diagonal) {
            speed /= 2.0F;
        }
        return speed;
    }
}
