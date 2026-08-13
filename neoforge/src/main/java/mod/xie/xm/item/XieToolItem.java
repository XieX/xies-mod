package mod.xie.xm.item;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A {@code tool} that declares {@code transforms}: right-click a block with it and
 * the block becomes another one.
 *
 * <p>The second class in {@code mod.xie.xm.item} and the second archetype in the
 * port to end with more code than it started with, for the reason
 * {@link XieBowItem} gives. Everything else {@code XieItemTool} did was a
 * description of a tool — how fast it mines, what it drops, what a swing is worth —
 * and {@code XieToolProperties} says all of it in data components with no class
 * involved. {@code transforms} is not a description. It is something the tool
 * <em>does</em> when you click, and nothing in the component vocabulary replaces
 * the block you clicked.
 *
 * <p>So only a definition that declares the property gets this class; the other
 * eight {@code tool} and {@code sword} declarations still dissolve completely, the
 * same way {@code boneBow} takes {@code super} on every branch of
 * {@link XieBowItem}. {@code ultimateFist} is the one that declares it, turning
 * stone into a gold block and dirt into air.
 *
 * <p>Vanilla writes exactly this shape twice over. {@code HoeItem.useOn} tills a
 * block into farmland and {@code ShovelItem.useOn} flattens one into a path: both
 * play a sound on <em>both</em> sides, change the block and spend one durability on
 * the server alone, and answer {@code SUCCESS}. That is
 * {@code XieItemTool.onItemUse} line for line, including the guard that a click on
 * the underside of a block does nothing — see {@link #useOn}.
 */
public class XieToolItem extends Item {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** {@code itemstack.damageItem(1, entityplayer)} — one point per transform. */
    private static final int WEAR_PER_TRANSFORM = 1;

    /**
     * One entry of a {@code transforms} map, as ids rather than blocks: this is
     * built while the block registry is still filling.
     *
     * @param from namespaced id of the block that is clicked
     * @param to   namespaced id of the block it becomes, which may be air
     */
    public record Transform(String from, String to) { }

    /**
     * @param transforms the declared pairs, in declaration order — the order they
     *                   are tried in, and the first match wins
     */
    public record Config(List<Transform> transforms) {

        public static final Config NONE = new Config(List.of());

        public Config {
            transforms = List.copyOf(transforms);
        }

        public boolean transformsAnything() {
            return !transforms.isEmpty();
        }
    }

    private final Config config;

    /**
     * The pairs resolved to live blocks, on first use rather than in the
     * constructor: a tool is created while the block registry is still filling, and
     * a pack may name a block that registers after it — or, being 2013 content, one
     * that no longer exists at all.
     */
    private volatile @Nullable List<Resolved> resolved;

    private record Resolved(Block from, BlockState to) { }

    public XieToolItem(Item.Properties properties, Config config) {
        super(properties);
        this.config = config;
    }

    public Config config() {
        return config;
    }

    /**
     * Right-click a block.
     *
     * <p>{@code XieItemTool.onItemUse}, in order, and every one of its decisions is
     * kept:
     *
     * <ul>
     *   <li><strong>The bottom face does nothing.</strong> The 1.6 guard is
     *       {@code l != 0}, where {@code l} is the clicked side and 0 is the
     *       underside — and it is not an artefact of how that version numbered
     *       faces, because vanilla still writes it: {@code ShovelItem.useOn} opens
     *       with {@code if (context.getClickedFace() == Direction.DOWN) return PASS}.
     *       {@code Direction.DOWN} is the same face and, as it happens, still the
     *       same ordinal. Reaching under a block to reshape it is the one angle from
     *       which you cannot see what you did.</li>
     *   <li><strong>{@code canPlayerEdit} is checked before anything happens</strong>,
     *       which is {@code Player.mayUseItemAt} — it only ever refuses in adventure
     *       mode, and the position and face it is asked about are the clicked
     *       ones.</li>
     *   <li><strong>The sound plays on both sides and the block changes on one.</strong>
     *       {@code if (world.isRemote) return true;} sits between the two, so the
     *       client that predicted the click hears it immediately and the server tells
     *       everybody else. {@code Level.playSound} takes the acting player as the
     *       listener to <em>exclude</em>, which is that arrangement written the
     *       modern way round, and is what {@code HoeItem} does with the tilling
     *       sound.</li>
     *   <li><strong>One durability per transform</strong>, and only on the server,
     *       for the same reason.</li>
     * </ul>
     *
     * <p>The one addition is the game event. 2013 had none; every vanilla tool that
     * changes a block posts {@code BLOCK_CHANGE} now, and without it a sculk sensor
     * is deaf to a gold block appearing in front of it.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Direction face = context.getClickedFace();
        if (player == null || face == Direction.DOWN) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack held = context.getItemInHand();
        if (!player.mayUseItemAt(pos, face, held)) {
            return InteractionResult.PASS;
        }

        BlockState clicked = level.getBlockState(pos);
        BlockState becomes = transformOf(clicked);
        if (becomes == null) {
            return InteractionResult.PASS;
        }

        playBreakSound(level, pos, player, clicked, becomes);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Xie.setBlockAndMeta passed flag 3: notify the neighbours and tell the
        // clients, which is Block.UPDATE_ALL.
        level.setBlock(pos, becomes, Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, becomes));
        held.hurtAndBreak(WEAR_PER_TRANSFORM, player, context.getHand().asEquipmentSlot());
        return InteractionResult.SUCCESS;
    }

    /**
     * What the clicked block becomes, or null when this tool has nothing to say
     * about it.
     *
     * <p>{@code getTransform} walked the map and returned the first key that
     * {@code BlockID.matches} answered to, which compared the block id and — where
     * the key carried one — the metadata. Metadata is a blockstate now and no
     * declaration carries one, so this is the block comparison alone: every state of
     * stone transforms, as every state of it did then.
     */
    private @Nullable BlockState transformOf(BlockState clicked) {
        for (Resolved transform : resolved()) {
            if (clicked.is(transform.from())) {
                return transform.to();
            }
        }
        return null;
    }

    /**
     * The break sound, which is the sound of whichever block the original picked.
     *
     * <p><strong>It is the sound of what appears, not of what was destroyed</strong>
     * — turning stone into gold plays the gold block breaking. The exception is the
     * transform to air, which has no sound of its own, and that is the whole of the
     * branch: {@code if (blockID.blockID == 0) block = Block.blocksList[world
     * .getBlockId(i, j, k)]}. Air falls back to the block that was there, which is
     * the half that reads as intended; the general case is the half that reads as
     * odd, and both are reproduced because neither is broken — a transform is not a
     * mining action and had no more claim on one sound than the other.
     *
     * <p>The volume and pitch are {@code Xie.playBreakSoundEffect}'s, which are
     * vanilla's own for a block sound. Its <em>position</em> is not: the call site
     * added half a block to each coordinate and the method added another half on
     * top, putting the sound a block away diagonally. That one is a slip rather than
     * a decision, and {@code Level.playSound} centres on the block.
     */
    private static void playBreakSound(Level level, BlockPos pos, Player player,
                                       BlockState clicked, BlockState becomes) {
        BlockState heard = becomes.isAir() ? clicked : becomes;
        SoundType sound = heard.getSoundType(level, pos, player);
        level.playSound(player, pos, sound.getBreakSound(), SoundSource.BLOCKS,
            (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
    }

    private List<Resolved> resolved() {
        List<Resolved> found = resolved;
        if (found == null) {
            List<Resolved> pairs = new ArrayList<>(config.transforms().size());
            for (Transform transform : config.transforms()) {
                Optional<Block> from = block(transform.from());
                Optional<Block> to = block(transform.to());
                if (from.isEmpty() || to.isEmpty()) {
                    LOGGER.warn("Tool {} transforms \"{}\" into \"{}\", and no block answers to {}",
                        BuiltInRegistries.ITEM.getKey(this), transform.from(), transform.to(),
                        from.isEmpty() ? transform.from() : transform.to());
                    continue;
                }
                pairs.add(new Resolved(from.get(), to.get().defaultBlockState()));
            }
            resolved = found = List.copyOf(pairs);
        }
        return found;
    }

    /**
     * A block by id. {@code getOptional} rather than {@code get} because the block
     * registry's default value <em>is</em> air, so an id nothing answers to would
     * otherwise come back as a perfectly plausible transform to nothing.
     */
    private static Optional<Block> block(String id) {
        return Optional.ofNullable(Identifier.tryParse(id)).flatMap(BuiltInRegistries.BLOCK::getOptional);
    }
}
