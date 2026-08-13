package mod.xie.xm.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

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
 *
 * <p>Everything else a bush does — growth, placement, per-age boxes, bone meal —
 * is the {@code plant} behaviour it inherits from {@link XiePlantBlock}, which is
 * the same class hierarchy the original used.
 */
public class XieBushBlock extends XiePlantBlock {

    /**
     * How far a ripe bush falls back when harvested, from the original's
     * {@code maxGrowth - 3}.
     */
    public static final int REGROW_SETBACK = 3;

    public static final MapCodec<XieBushBlock> CODEC =
        simpleCodec(properties -> new XieBushBlock(properties, Config.PLACEHOLDER));

    public XieBushBlock(BlockBehaviour.Properties properties, Config config) {
        super(properties, config);
    }

    @Override
    protected MapCodec<? extends XieBushBlock> codec() {
        return CODEC;
    }

    /** The stage a harvested bush falls back to. */
    public int regrowAge() {
        return Math.max(0, config().maxAge() - REGROW_SETBACK);
    }

    /**
     * The perennial harvest: pick a ripe bush and it drops its fruit and falls back
     * three growth stages instead of being destroyed.
     *
     * <p>The original did this on <em>break</em>, from
     * {@code XieBlockBush.onBlockDestroyedByPlayer}. Doing it on right-click instead
     * follows {@code SweetBerryBushBlock}, which arrived in 1.14 with the same
     * mechanic — Mojang reached the same design independently, and matching their
     * idiom is what makes the block feel native. It also behaves consistently in
     * creative, where block-break never reaches {@code playerDestroy} at all.
     *
     * <p>Breaking still works and still drops, it just removes the bush like any
     * other plant. Harvest is the non-destructive option, not the only one.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!isRipe(state)) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }

        if (level instanceof ServerLevel serverLevel) {
            // Drops resolve against the ripe state, so the block's own loot table
            // yields exactly what breaking it at this age would have.
            Block.dropResources(state, serverLevel, pos, null, player, player.getMainHandItem());

            serverLevel.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                SoundSource.BLOCKS, 1.0F, 0.8F + serverLevel.getRandom().nextFloat() * 0.4F);

            BlockState picked = state.setValue(ageProperty(), regrowAge());
            serverLevel.setBlock(pos, picked, Block.UPDATE_CLIENTS);
            serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, picked));
        }
        return InteractionResult.SUCCESS;
    }
}
