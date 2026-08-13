package mod.xie.xm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * {@code becomesWhenDestroyed}: the block left behind when a player breaks this one.
 *
 * <p>{@code XieBlock.onBlockDestroyedByPlayer} was copied verbatim into every one of
 * the original's block classes — {@code XieBlockLog} and {@code XieBlockStem} both
 * carry it under a {@code // copy-pasta from XieBlock} comment — so the archetypes
 * that need a class of their own here share one implementation instead.
 *
 * <p>Resolution is lazy for the reason every other reference in the port is: blocks
 * are created while the block registry is still filling, so the block this one
 * becomes may not exist yet, and with content packs may not exist at all.
 */
final class XieBecomes {

    private final Optional<String> id;
    private volatile Optional<BlockState> resolved;

    XieBecomes(Optional<String> id) {
        this.id = id;
    }

    /**
     * Leaves the replacement behind, if there is one.
     *
     * <p>{@link Block#destroy} is the hook with {@code onBlockDestroyedByPlayer}'s
     * exact semantics: {@code ServerPlayerGameMode.removeBlock} calls it after the
     * block has been cleared, on both the survival and the creative branch — unlike
     * {@code playerDestroy}, which creative never reaches. Nothing else destroys a
     * block through it, so an explosion still just removes it, as it did in 1.6.
     *
     * <p>{@code MultiPlayerGameMode} calls it client-side too, as a prediction; that
     * client is told what happened by the server's own block update, so it is left to
     * be.
     */
    void applyOnDestroy(LevelAccessor level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        state().ifPresent(become -> level.setBlock(pos, become, Block.UPDATE_ALL));
    }

    private Optional<BlockState> state() {
        Optional<BlockState> found = resolved;
        if (found == null) {
            resolved = found = id
                .map(Identifier::tryParse)
                .flatMap(BuiltInRegistries.BLOCK::getOptional)
                .map(Block::defaultBlockState);
        }
        return found;
    }
}
