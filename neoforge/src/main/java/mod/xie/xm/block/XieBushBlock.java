package mod.xie.xm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.level.storage.loot.LootTable;
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
 * <p>In 1.6, break a ripe bush and it was not removed — it dropped its fruit and fell
 * back three growth stages. Here that harvest is a right-click; see
 * {@link #useWithoutItem}. A crop, by contrast, is annual: harvesting destroys it and
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

    private final boolean bears;
    private ResourceKey<LootTable> harvestTable;

    /** @param bears whether the content gave it a {@code fruit}; a bush without one is never picked */
    public XieBushBlock(BlockBehaviour.Properties properties, Config config, boolean bears) {
        super(properties, config);
        this.bears = bears;
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
     * <p>The fruit comes out of the loot table at {@link XieTreeBlock#HARVEST_PREFIX},
     * which {@code XieDataEmitter} writes from {@code fruit}: the same split vanilla
     * makes between {@code harvest/sweet_berry_bush} and the berry bush's block loot.
     * The original had one table for both, which it could afford because breaking a
     * ripe bush never removed it. Breaking one here removes it like any other plant,
     * so {@code drops} is what it leaves to replant from and {@code fruit} is the crop.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!bears || !isRipe(state)) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }

        if (level instanceof ServerLevel serverLevel) {
            // A bush is hollow, so the fruit drops where it hangs, as berries do.
            Block.dropFromBlockInteractLootTable(serverLevel, harvestTable(), pos, state, null, null,
                player, (where, stack) -> Block.popResource(where, pos, stack));

            serverLevel.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                SoundSource.BLOCKS, 1.0F, 0.8F + serverLevel.getRandom().nextFloat() * 0.4F);

            BlockState picked = state.setValue(ageProperty(), regrowAge());
            serverLevel.setBlock(pos, picked, Block.UPDATE_CLIENTS);
            serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, picked));
        }
        return InteractionResult.SUCCESS;
    }

    private ResourceKey<LootTable> harvestTable() {
        ResourceKey<LootTable> key = harvestTable;
        if (key == null) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(this);
            harvestTable = key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(
                id.getNamespace(), XieTreeBlock.HARVEST_PREFIX + id.getPath()));
        }
        return key;
    }
}
