package mod.xie.xm.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * A {@code seeds} item that declares a {@code container}, which it hands back when it
 * plants: placing a bucket of cheese leaves you the bucket.
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
    private final Item container;

    public XieSeedItem(Block block, Item container, Properties properties) {
        super(block, properties);
        this.container = container;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult placed = super.useOn(context);
        Player player = context.getPlayer();
        if (!placed.consumesAction() || player == null || player.hasInfiniteMaterials()) {
            return placed;
        }

        ItemStack emptied = new ItemStack(container);
        if (context.getItemInHand().isEmpty()) {
            player.setItemInHand(context.getHand(), emptied);
        } else if (!player.getInventory().add(emptied)) {
            player.drop(emptied, false);
        }
        return placed;
    }
}
