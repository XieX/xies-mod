package mod.xie.xm.item;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@code stick} archetype: right-click a listed block and harvest it in place.
 *
 * <p>The third piece of behaviour in {@code mod.xie.xm.item}, and it follows from the
 * other two rather than adding anything new. Like {@code transforms} on
 * {@link XieToolItem} it is a {@code useOn} that changes the block you clicked, and
 * like {@link XieBowItem} it is a decision rather than a description — what a stick
 * yields is not what the block it hits drops, so no loot table can carry it.
 *
 * <p>{@code XieItemStick} is seventy-nine lines and holds two lists walked in step: a
 * {@code BlockID} and, at the same index, the {@code Droppables} that block yields.
 * Right-clicking a listed block plays a crunch, spawns that block's drops, and on a
 * {@code destroyChance} roll clears the block to air. It costs one durability out of
 * sixteen and stacks to one.
 *
 * <p>The shape of the method is {@link XieToolItem#useOn}'s, and for the reason given
 * there: {@code HoeItem.useOn} and {@code ShovelItem.useOn} are both this method, so
 * the sound plays on both sides, the world changes and the durability is spent on the
 * server alone, and the answer is an {@code InteractionResult} rather than a boolean.
 * Two things differ, and both are the 2013 method's own:
 *
 * <ul>
 *   <li><strong>Every face works.</strong> {@code XieItemTool} guarded on {@code l != 0}
 *       and this did not. That reads as deliberate rather than forgotten — a tool
 *       reshapes the block's surface and wants you to see the face you changed, and a
 *       sickle takes the whole block, which looks the same from underneath.</li>
 *   <li><strong>The block's own drops are not run.</strong> The line that would have,
 *       {@code harvestBlock}, is commented out in the original. What the definition
 *       lists is the whole of what you get.</li>
 * </ul>
 *
 * <p>One thing is added. {@code canPlayerEdit} — {@code Player#mayUseItemAt} — is
 * checked before anything happens, which {@code XieItemTool.onItemUse} did and this
 * did not. The omission is not a design decision expressed in code: it lets an
 * adventure-mode player clear blocks that the gamemode exists to protect, and every
 * vanilla item that changes a block asks first. It is the same trade
 * {@code XieBasicBlock.canSurvive} makes — faithful and reads as a bug.
 */
public class XieStickItem extends Item {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** {@code itemstack.damageItem(1, entityplayer)} — one point per use. */
    private static final int WEAR_PER_USE = 1;

    /** About what vanilla's destroy burst puts out for a plant-sized shape. */
    private static final int DESTROY_PARTICLES = 24;

    /** {@code setMaxDamage(16)}, which a definition's {@code uses} overrides. */
    public static final int DEFAULT_USES = 16;

    /** {@code protected int destroyChance = 66} — two uses in three take the block. */
    public static final int DEFAULT_DESTROY_CHANCE = 66;

    /**
     * One entry of a {@code blocks} map, as an id rather than a block: this is built
     * while the block registry is still filling.
     *
     * @param block namespaced id of the block that has to be clicked
     * @param drops what it yields, each with a percentage chance and a number of
     *              independent rolls — {@code Droppables} semantics, the same ones a
     *              block's loot table is built from
     */
    public record Harvest(String block, List<Drop> drops) {

        public Harvest {
            drops = List.copyOf(drops);
        }
    }

    /**
     * @param item   namespaced id of what drops
     * @param chance percentage, rolled once per roll rather than once per use
     * @param rolls  {@code amount}: independent rolls, not a stack size
     * @param min    how many of those rolls always pay, while the chance is above zero
     */
    public record Drop(String item, int chance, int rolls, int min) { }

    /**
     * @param harvests      the declared blocks in declaration order — the order they
     *                      are tried in, and the first match wins
     * @param destroyChance percentage chance the block is cleared to air
     */
    public record Config(List<Harvest> harvests, int destroyChance) {

        public Config {
            harvests = List.copyOf(harvests);
            destroyChance = Math.clamp(destroyChance, 0, 100);
        }

        public boolean harvestsAnything() {
            return !harvests.isEmpty();
        }
    }

    private final Config config;

    /**
     * The entries resolved to live blocks and items, on first use rather than in the
     * constructor: a stick is created while the registries are still filling, and a
     * pack may name something that registers after it — or, being 2013 content, one
     * that no longer exists at all. See {@link XieToolItem#useOn}.
     */
    private volatile @Nullable List<Resolved> resolved;

    private record Resolved(Block block, List<ResolvedDrop> drops) { }

    private record ResolvedDrop(Item item, int chance, int rolls, int min) { }

    public XieStickItem(Item.Properties properties, Config config) {
        super(properties);
        this.config = config;
    }

    public Config config() {
        return config;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack held = context.getItemInHand();
        if (!player.mayUseItemAt(pos, context.getClickedFace(), held)) {
            return InteractionResult.PASS;
        }

        BlockState clicked = level.getBlockState(pos);
        Resolved harvest = harvestOf(clicked);
        if (harvest == null) {
            return InteractionResult.PASS;
        }

        playCrunch(level, pos, player, clicked);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // The particles are the server's to send now. Xie.addBlockDestroyEffects went
        // through the proxy and so ran only on the client that swung — the dedicated
        // server's implementation is an empty method — which meant nobody else saw
        // it. This is the same burst, and everybody gets it. Sent as particles rather
        // than LevelEvent.PARTICLES_DESTROY_BLOCK, which plays the break sound too.
        if (level instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, clicked),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                DESTROY_PARTICLES, 0.25, 0.25, 0.25, 0.05);
        }

        spawnDrops(level, pos, harvest);

        // The drops come first and the block goes second, and only sometimes: the
        // roll decides whether the plant survives being picked, not whether it yields.
        if (level.getRandom().nextInt(100) < config.destroyChance()) {
            // setBlock rather than destroyBlock: the block's own loot table is not
            // run. The original's call to harvestBlock is commented out in place.
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, clicked));
        }

        held.hurtAndBreak(WEAR_PER_USE, player, context.getHand().asEquipmentSlot());
        return InteractionResult.SUCCESS;
    }

    /** The entry for the clicked block, or null when this stick has nothing to say. */
    private @Nullable Resolved harvestOf(BlockState clicked) {
        for (Resolved harvest : resolved()) {
            if (clicked.is(harvest.block())) {
                return harvest;
            }
        }
        return null;
    }

    /**
     * {@code Xie.playBlockCrunch}: the clicked block's <em>step</em> sound, at the
     * volume and pitch vanilla uses for a block sound.
     *
     * <p>A step rather than a break, which is the one thing that makes it a crunch and
     * not a mining noise — and it plays whether or not the block is taken, because in
     * the original it played before the roll. Unlike {@link XieToolItem} there is no
     * question of <em>which</em> block's sound: nothing appears in its place.
     */
    private static void playCrunch(Level level, BlockPos pos, Player player, BlockState clicked) {
        SoundType sound = clicked.getSoundType(level, pos, player);
        level.playSound(player, pos, sound.getStepSound(), SoundSource.BLOCKS,
            (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
    }

    /**
     * {@code Droppables.getDrops}, which is {@code getDropsForMetaAndMultiplier(-1, 0)}:
     * every drop rolled {@code amount} times, each roll yielding a single item at its
     * chance, with no fortune multiplier and no metadata.
     *
     * <p>The metadata of {@code -1} is why the chance is read at zero here.
     * {@code Drop.getDropForMeta} fell straight through to {@code metaChance[0]} for a
     * negative value, so a per-metadata chance written on a stick's drops never meant
     * anything — and could not, since the number would have been a growth stage of a
     * block the stick does not own.
     */
    private static void spawnDrops(Level level, BlockPos pos, Resolved harvest) {
        RandomSource random = level.getRandom();
        for (ResolvedDrop drop : harvest.drops()) {
            for (int roll = 0; roll < drop.rolls(); roll++) {
                if (drop.chance() > 0 && (roll < drop.min() || random.nextInt(100) < drop.chance())) {
                    // Block.popResource is Xie.spawnItem: a small random offset from
                    // the block's centre and a pickup delay, which is what its own
                    // "delayBeforeCanPickup = 10" was for.
                    Block.popResource(level, pos, new ItemStack(drop.item()));
                }
            }
        }
    }

    private List<Resolved> resolved() {
        List<Resolved> found = resolved;
        if (found == null) {
            List<Resolved> harvests = new ArrayList<>(config.harvests().size());
            for (Harvest harvest : config.harvests()) {
                block(harvest.block()).ifPresentOrElse(
                    block -> harvests.add(new Resolved(block, resolveDrops(harvest))),
                    () -> LOGGER.warn("Stick {} works on \"{}\", which no block answers to",
                        BuiltInRegistries.ITEM.getKey(this), harvest.block()));
            }
            resolved = found = List.copyOf(harvests);
        }
        return found;
    }

    /**
     * A drop naming nothing that exists is left out and said so, which is what the
     * emitter does with a block's. In 2013 it took the whole item with it —
     * {@code JobItem.doStick} set {@code problemFlag} and the job never completed — so
     * a stick with one bad name registered nothing at all.
     */
    private List<ResolvedDrop> resolveDrops(Harvest harvest) {
        List<ResolvedDrop> drops = new ArrayList<>(harvest.drops().size());
        for (Drop drop : harvest.drops()) {
            Optional<Item> item = Optional.ofNullable(Identifier.tryParse(drop.item()))
                .flatMap(BuiltInRegistries.ITEM::getOptional);
            if (item.isEmpty()) {
                LOGGER.warn("Stick {} drops \"{}\" from {}, and no item answers to it; "
                        + "that drop is left out",
                    BuiltInRegistries.ITEM.getKey(this), drop.item(), harvest.block());
                continue;
            }
            drops.add(new ResolvedDrop(item.get(), drop.chance(), drop.rolls(), drop.min()));
        }
        return drops;
    }

    /**
     * A block by id. {@code getOptional} rather than {@code get} because the block
     * registry's default value <em>is</em> air, so an id nothing answers to would
     * otherwise come back as a stick that harvests thin air.
     */
    private static Optional<Block> block(String id) {
        return Optional.ofNullable(Identifier.tryParse(id)).flatMap(BuiltInRegistries.BLOCK::getOptional);
    }
}
