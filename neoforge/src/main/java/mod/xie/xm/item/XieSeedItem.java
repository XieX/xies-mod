package mod.xie.xm.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Optional;

/**
 * A {@code seeds} item: a {@link BlockItem} for the block it plants, which hands back
 * its {@code container} when it plants — placing a bucket of cheese leaves you the
 * bucket.
 *
 * <p>A seed whose block has an item of its own, as an apple seed's sapling does, does
 * not claim the block's item mapping. That mapping is {@code Block.asItem}, which is
 * what pick-block and anything else making a stack of the block reads, and the last
 * {@link BlockItem} registered for a block wins it. Vanilla's wheat seeds claim the
 * wheat crop because the crop has no other item.
 *
 * <p>This is a deliberate deviation. {@code container} went to
 * {@code Item.setContainerItem} in 2013, whose whole effect was on the crafting grid,
 * and {@code XieItemSeeds} placed its block and shrank the stack like any other seed —
 * so a bucket of cheese set down by hand lost its bucket. Crafting it into the block
 * gave the bucket back, and the two routes to the same block disagreeing read as a
 * bug rather than a design. The live content has since dropped that recipe, so
 * pouring the bucket out is now the only way to the block, and losing the bucket
 * would make it cost one every time. Eating already honours {@code container} the
 * same way, as the use remainder.
 *
 * <p>The shape is vanilla's {@code SolidBucketItem}, the powder snow bucket, which is a
 * {@link BlockItem} that swaps itself for an empty bucket once {@code super.useOn} has
 * placed. That one stacks to one and can assume the hand is empty afterwards; a
 * container here is whatever the content names on whatever stack size it declares, so
 * the rest is {@code ItemUtils.createFilledResult}'s — the container replaces an
 * emptied stack, and otherwise goes to the inventory or at the player's feet. A player
 * with infinite materials was never charged the seed, so is given nothing back.
 */
public class XieSeedItem extends BlockItem {
    private final Optional<Item> container;
    private final boolean claimsBlock;

    public XieSeedItem(Block block, Optional<Item> container, boolean claimsBlock,
                       Properties properties) {
        super(block, properties);
        this.container = container;
        this.claimsBlock = claimsBlock;
    }

    @Override
    public void registerBlocks(Map<Block, Item> map, Item item) {
        if (claimsBlock) {
            super.registerBlocks(map, item);
        }
    }

    /**
     * Hands the container back from {@code place} rather than {@code useOn}, because
     * {@code BlockItem.useOn} falls back to eating a consumable item that failed to
     * place, and that counts as an action too.
     */
    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult placed = super.place(context);
        Player player = context.getPlayer();
        if (container.isEmpty() || !placed.consumesAction() || player == null
                || player.hasInfiniteMaterials()) {
            return placed;
        }

        ItemStack emptied = new ItemStack(container.get());
        if (context.getItemInHand().isEmpty()) {
            player.setItemInHand(context.getHand(), emptied);
        } else if (!player.getInventory().add(emptied)) {
            player.drop(emptied, false);
        }
        return placed;
    }
}
