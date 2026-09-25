package mod.xie.xm.block;

import com.mojang.serialization.MapCodec;

import mod.xie.xm.cook.StovePot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;
import java.util.Set;

/**
 * The {@code stove} archetype: a cooking pot you feed one ingredient at a time.
 *
 * <p>Rebuilt from the original mod posting rather than ported — the Cooking Mod
 * content pack that held the 2013 stove did not survive. {@link StovePot} carries
 * the recipe rules and the reasoning behind them; this class is the interaction.
 *
 * <p>Everything goes in by hand, with no menu:
 *
 * <ul>
 *   <li>water on an empty stove starts a pot;</li>
 *   <li>anything in the {@code ingredients} tag is a food and anything in
 *       {@code flavourings} is a seasoning; either may go in at any time, and each
 *       restarts the cook;</li>
 *   <li>an empty bowl ladles out the best meal the pot can afford, once it has
 *       finished cooking;</li>
 *   <li>any furnace fuel stokes the fire.</li>
 * </ul>
 *
 * <p>The 2013 stove had a menu, and the posting calls it "an upgraded version of the
 * furnace", but it never smelted the way one does — the pot was always the whole of
 * it. Dropping the menu for right-click therefore costs nothing that existed, and
 * buys an interaction that does not need a screen to explain itself.
 *
 * <p>An item that is both fuel and an ingredient resolves as an ingredient. Nothing
 * in the current content is both, but the content decides what goes in these lists
 * and the order the click is resolved in should not depend on that staying true.
 * A bowl is the one item that is never fuel here, whatever vanilla's burn table
 * says: a bowl on a pot that has nothing to serve is refused, not burnt.
 */
public class XieStoveBlock extends Block implements EntityBlock {

    /** Whether the fire is in. */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    /**
     * What is in the pot and whether it is done, in one value.
     *
     * <p>This is the mod posting's "wait until the pot changes colour". Each value is
     * one cauldron model with a different {@code content} texture, so the pot really
     * does change colour as it cooks: clear water darkens to stew. See
     * {@code XiePackEmitter.STOVE_CONTENTS} for the three textures.
     *
     * <p>Base and doneness share one property rather than taking two, because every
     * combination of them is exactly one model. With the oil base cut there is only
     * one liquid, so the two are currently in step; the property is still the pot's
     * appearance rather than its contents, and a second base would widen it here.
     */
    public static final IntegerProperty POT = IntegerProperty.create("pot", 0, 2);

    public static final int POT_EMPTY = 0;
    public static final int POT_WATER_RAW = 1;
    public static final int POT_WATER_READY = 2;

    /**
     * @param cookTime        ticks the pot takes to come up to a servable meal
     * @param fuelCapacity    most fuel the pot will hold, in ticks
     * @param foodCapacity    how many foods the pot holds at once
     * @param flavourCapacity how many flavourings the pot holds at once
     * @param ingredients     tag of things that count as a food ingredient
     * @param flavourings     tag of things that count as a flavouring
     * @param waterItems      ids that start a pot
     */
    public record Config(int cookTime, int fuelCapacity, int foodCapacity, int flavourCapacity,
                         Optional<TagKey<Item>> ingredients, Optional<TagKey<Item>> flavourings,
                         Set<Identifier> waterItems) {

        public Config {
            waterItems = Set.copyOf(waterItems);
        }

        /** Used only to satisfy the block codec; never placed in a world. */
        public static final Config PLACEHOLDER = new Config(
            200, 6000, 8, 4, Optional.empty(), Optional.empty(), Set.of());
    }

    public static final MapCodec<XieStoveBlock> CODEC =
        simpleCodec(properties -> new XieStoveBlock(properties, Config.PLACEHOLDER));

    private final Config config;

    public XieStoveBlock(BlockBehaviour.Properties properties, Config config) {
        super(properties);
        this.config = config;
        registerDefaultState(getStateDefinition().any()
            .setValue(LIT, false)
            .setValue(POT, POT_EMPTY));
    }

    public Config config() {
        return config;
    }

    /** The config of whatever stove a state belongs to, for the block entity to read. */
    public static Config configOf(BlockState state) {
        return state.getBlock() instanceof XieStoveBlock stove ? stove.config() : Config.PLACEHOLDER;
    }

    @Override
    protected MapCodec<? extends XieStoveBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, POT);
    }

    /**
     * Vanilla's cauldron shape, rebuilt rather than borrowed.
     *
     * <p>{@code AbstractCauldronBlock.SHAPE} is protected and a stove is not a
     * cauldron, so the construction is copied: a full block with the bowl and the
     * gaps between the legs cut out of it. Copied exactly, so a stove and a cauldron
     * feel identical underfoot and the pot can be seen into.
     */
    private static final VoxelShape SHAPE_INSIDE = Block.column(12.0, 4.0, 16.0);

    private static final VoxelShape SHAPE = Shapes.join(
        Shapes.block(),
        Shapes.or(Block.column(16.0, 8.0, 0.0, 3.0),
                  Block.column(8.0, 16.0, 0.0, 3.0),
                  Block.column(12.0, 0.0, 3.0),
                  SHAPE_INSIDE),
        BooleanOp.ONLY_FIRST);

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE_INSIDE;
    }

    /**
     * Fire under a lit stove, and steam off a pot that is still cooking.
     *
     * <p>A cauldron has no furnace front to glow, so the fire is particles between
     * its legs. The steam is the other half of telling a player where the pot is up
     * to: the colour says what is in it, the steam says it is still working.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;

        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.FLAME,
                x + (random.nextDouble() - 0.5) * 0.5, y + 0.1,
                z + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.0, 0.0);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.SMOKE,
                x + (random.nextDouble() - 0.5) * 0.6, y + 0.1,
                z + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.0, 0.0);
        }

        if (state.getValue(POT) == POT_WATER_RAW) {
            level.addParticle(ParticleTypes.CLOUD,
                x + (random.nextDouble() - 0.5) * 0.4, y + 0.95,
                z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.02, 0.0);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return XieStoveBlockEntity.type() == null ? null : new XieStoveBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        BlockEntityType<XieStoveBlockEntity> ours = XieStoveBlockEntity.type();
        if (ours == null || type != ours) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) ->
            XieStoveBlockEntity.serverTick(tickLevel, pos, tickState, (XieStoveBlockEntity) entity);
    }

    // -- interaction ----------------------------------------------------------

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level.getBlockEntity(pos) instanceof XieStoveBlockEntity stove)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            // Whether the click will land is decided server-side; the client swings
            // for anything the stove could plausibly take so the two do not disagree.
            return couldAccept(stack, stove) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        if (tryBase(stack, stove, player, level, pos)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (tryBowl(stack, stove, player, level, pos)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (isBowl(stack)) {
            // A bowl is furnace fuel in vanilla, so without this a bowl on a pot
            // with nothing to serve would fall through and be burnt. It is the
            // acceptance run's first finding: the player's bowl is never fuel.
            return InteractionResult.PASS;
        }
        if (tryIngredient(stack, stove, player, level, pos)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (tryFuel(stack, stove, player, level, pos)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    /** A cheap client-side guess at whether the server will take this item. */
    private boolean couldAccept(ItemStack stack, XieStoveBlockEntity stove) {
        return baseOf(stack).isPresent()
            || isBowl(stack)
            || isIngredient(stack)
            || isFlavouring(stack)
            || stove.burnTime() < config.fuelCapacity();
    }

    private boolean tryBase(ItemStack stack, XieStoveBlockEntity stove, Player player,
                            Level level, BlockPos pos) {
        Optional<StovePot.Base> base = baseOf(stack);
        if (base.isEmpty() || !stove.setBase(base.get())) {
            return false;
        }
        consumeOne(stack, player);
        playSound(level, pos, SoundEvents.BUCKET_EMPTY);
        updateAppearance(level, pos, level.getBlockState(pos), stove);
        return true;
    }

    private boolean tryBowl(ItemStack stack, XieStoveBlockEntity stove, Player player,
                            Level level, BlockPos pos) {
        if (!isBowl(stack)) {
            return false;
        }
        Optional<StovePot.Meal> meal = stove.extract();
        if (meal.isEmpty()) {
            return false;
        }

        ItemStack served = mealStack(meal.get());
        if (served.isEmpty()) {
            // The meal item is not registered, which means the content and the state
            // machine have drifted apart. Refuse rather than eating the player's bowl.
            return false;
        }

        stack.shrink(1);
        if (!player.getInventory().add(served)) {
            player.drop(served, false);
        }
        playSound(level, pos, SoundEvents.BREWING_STAND_BREW);
        updateAppearance(level, pos, level.getBlockState(pos), stove);
        return true;
    }

    private boolean tryIngredient(ItemStack stack, XieStoveBlockEntity stove, Player player,
                                  Level level, BlockPos pos) {
        boolean flavouring = isFlavouring(stack);
        if (!flavouring && !isIngredient(stack)) {
            return false;
        }
        if (!stove.addIngredient(flavouring)) {
            return false;
        }
        consumeOne(stack, player);
        playSound(level, pos, SoundEvents.GENERIC_SPLASH);
        updateAppearance(level, pos, level.getBlockState(pos), stove);
        return true;
    }

    private boolean tryFuel(ItemStack stack, XieStoveBlockEntity stove, Player player,
                            Level level, BlockPos pos) {
        int burn = level.fuelValues().burnDuration(stack);
        if (burn <= 0 || !stove.addFuel(burn)) {
            return false;
        }
        consumeOne(stack, player);
        playSound(level, pos, SoundEvents.FIRECHARGE_USE);
        updateAppearance(level, pos, level.getBlockState(pos), stove);
        return true;
    }

    // -- item classification --------------------------------------------------

    private Optional<StovePot.Base> baseOf(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (config.waterItems().contains(id)) {
            return Optional.of(StovePot.Base.WATER);
        }
        return Optional.empty();
    }

    private boolean isBowl(ItemStack stack) {
        return stack.getItem() == Items.BOWL;
    }

    private boolean isIngredient(ItemStack stack) {
        return config.ingredients().isPresent()
            && stack.is(holder -> holder.is(config.ingredients().get()));
    }

    private boolean isFlavouring(ItemStack stack) {
        return config.flavourings().isPresent()
            && stack.is(holder -> holder.is(config.flavourings().get()));
    }

    /** The registered meal item, or empty if the content does not declare it. */
    private static ItemStack mealStack(StovePot.Meal meal) {
        Identifier id = Identifier.fromNamespaceAndPath(mod.xie.xm.XiesMod.MOD_ID, meal.path());
        return BuiltInRegistries.ITEM.getOptional(id)
            .map(ItemStack::new)
            .orElse(ItemStack.EMPTY);
    }

    // -- helpers --------------------------------------------------------------

    /**
     * Take one from the stack and hand back whatever the item leaves behind, so a
     * water bucket comes back as an empty bucket rather than vanishing.
     */
    private static void consumeOne(ItemStack stack, Player player) {
        ItemStackTemplate leftOver = stack.getItem().getCraftingRemainder();
        stack.shrink(1);
        if (leftOver == null) {
            return;
        }
        ItemStack remainder = leftOver.create();
        if (!remainder.isEmpty() && !player.getInventory().add(remainder)) {
            player.drop(remainder, false);
        }
    }

    private static void playSound(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.6f, 1.0f);
    }

    /**
     * Push the block entity's state into the blockstate, so the client sees the fire
     * and the pot without the block entity being synced.
     */
    public static void updateAppearance(Level level, BlockPos pos, BlockState state,
                                        XieStoveBlockEntity stove) {
        if (!(state.getBlock() instanceof XieStoveBlock)) {
            return;
        }
        BlockState updated = state
            .setValue(LIT, stove.isLit())
            .setValue(POT, stove.appearance());
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }
}
