package mod.xie.xm.reg;

import mod.xie.xm.content.model.BlockDef;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.Locale;

/**
 * Translates a definition's physical properties into {@code BlockBehaviour.Properties}.
 *
 * <p>The 1.6 {@code Material} class carried hardness defaults, sound, map colour,
 * piston behaviour and more in one object. It was removed in 1.20 and its
 * responsibilities split across separate builder calls, so a legacy
 * {@code "material": "sand"} has to fan out into several of them here.
 */
public final class XieBlockProperties {

    private XieBlockProperties() { }

    /**
     * Properties for a {@code stove}: vanilla's furnace, which is what it is built from.
     *
     * <p>Hardness 3.5 and a pickaxe requirement are {@code Blocks.FURNACE}'s own, and
     * the recipe puts a furnace in the middle of the stove, so inheriting them keeps
     * the upgrade from being softer than the thing it upgrades.
     *
     * <p>The light comes from the {@code lit} blockstate rather than the definition,
     * for the same reason a furnace's does: an unlit stove that glowed would be
     * indistinguishable from a lit one in a dark kitchen.
     *
     * <p>{@code noOcclusion} because the stove is drawn as a cauldron and a cauldron
     * is hollow. Without it the pot would be lit as though it were a solid cube and
     * the inside would go black.
     */
    public static BlockBehaviour.Properties forStove(BlockDef def) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
            .mapColor(mapColorFor(def))
            .sound(soundFor(def))
            .requiresCorrectToolForDrops()
            .noOcclusion()
            .lightLevel(state -> state.getValue(
                mod.xie.xm.block.XieStoveBlock.LIT) ? 13 : 0);

        return common(def, properties, 3.5f);
    }

    /** Properties for a plant-like block: no collision, ticks, breaks instantly. */
    public static BlockBehaviour.Properties forPlant(BlockDef def) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
            .mapColor(mapColorFor(def))
            .sound(soundFor(def))
            .noCollision()
            .randomTicks()
            .pushReaction(PushReaction.POPPED);

        return common(def, properties, 0f);
    }

    /**
     * Properties for a {@code basic} block: a plain solid block unless the
     * definition says otherwise.
     *
     * <p>{@code walkthroughable} is the one structural property, and it is applied
     * here rather than on the block because collision is decided before a
     * {@code Block} exists. {@code noCollision()} also clears {@code canOcclude},
     * which is what {@code XieBlock.setIsOpaque(false)} did in 1.6 — a shrub you
     * can walk through should not cast a full block's shadow either.
     *
     * <p>Hardness defaults to zero because that is what 1.6's {@code Block} did when
     * a definition declared none, and the content declares one wherever it matters
     * ({@code shrub} asks for 0.2). A {@code saltDeposit} you brush aside instantly
     * is the behaviour the original shipped.
     */
    public static BlockBehaviour.Properties forBasic(BlockDef def) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
            .mapColor(mapColorFor(def))
            .sound(soundFor(def));

        if (def.props().getBoolean("walkthroughable", false)) {
            properties = properties.noCollision().pushReaction(PushReaction.POPPED);
        }
        if (!fillsItsCube(def)) {
            // XieBlock.setBlockBounds called setIsOpaque(false) whenever the box was
            // not the whole cube, whatever else the definition said.
            properties = properties.noOcclusion();
        }

        return common(def, properties, 0f);
    }

    /**
     * Properties for a {@code ripening} block: a basic block that random-ticks.
     *
     * <p>It is terrain rather than vegetation — solid, standing on anything — so it
     * starts from {@link #forBasic} and adds only the ticks it ages on.
     */
    public static BlockBehaviour.Properties forRipening(BlockDef def) {
        return forBasic(def).randomTicks();
    }

    /**
     * Properties for a {@code log}: vanilla's own log, which the original's was too.
     *
     * <p>{@code XieBlockLog}'s constructor is four calls long — hardness 2.0, a wood
     * step sound (set by {@code JobBlock} rather than the class, which is why it is a
     * default here and not a fallback), flammability, and effective against every axe
     * — and the first two are these. The other two are elsewhere: flammability is on
     * {@link mod.xie.xm.block.XieLogBlock} because NeoForge asks the block, and axes
     * come with {@code #minecraft:logs}, which is also what stops a fruit tree's
     * leaves decaying off a trunk of this mod's own.
     *
     * <p>{@code noOcclusion} when the block does not fill its cube, which is
     * {@code XieBlock.setBlockBounds} calling {@code setIsOpaque(false)} for exactly
     * that case. A four-pixel trunk that still cast a full block's shadow would put a
     * dark column through the canopy above it.
     */
    public static BlockBehaviour.Properties forLog(BlockDef def) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
            .mapColor(def.props().has("material") ? mapColorFor(def) : MapColor.WOOD)
            .sound(def.props().has("stepSound") || def.props().has("stepsound")
                ? soundFor(def)
                : SoundType.WOOD)
            .ignitedByLava();

        if (!fillsItsCube(def)) {
            properties = properties.noOcclusion();
        }
        return common(def, properties, LOG_HARDNESS);
    }

    /** {@code XieBlockLog} called {@code setHardness(2.0F)}, as vanilla still does. */
    private static final float LOG_HARDNESS = 2.0f;

    /**
     * Properties for a {@code stem}, which are vanilla's own melon stem's.
     *
     * <p>{@code XieBlockStem} set nothing at all beyond its name: it took
     * {@code BlockStem}'s constructor and left every physical property alone, so
     * whatever 1.6 gave a melon stem is what a Xie stem had. That is still true.
     */
    public static BlockBehaviour.Properties forStem(BlockDef def) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .sound(SoundType.HARD_CROP)
            .pushReaction(PushReaction.POPPED);

        return common(def, properties, 0f);
    }

    /**
     * Properties for the attached half of a {@code stem} — the bent one, once it has
     * fruited.
     *
     * <p>It has no definition of its own to read, because in 1.6 it was not a block:
     * {@code BlockStem} decided at render time which way to bend by looking for its
     * fruit among the four neighbours. Vanilla has spent that on a blockstate since
     * 1.8, so this takes vanilla's own properties for one — which differ from the
     * growing stem's in exactly one respect, a wood step sound rather than a crop's.
     */
    public static BlockBehaviour.Properties forAttachedStem() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .instabreak()
            .sound(SoundType.WOOD)
            .pushReaction(PushReaction.POPPED);
    }

    /**
     * Whether a definition's {@code blockBounds} leave it a full cube.
     *
     * <p>Absent bounds are a full cube; so are bounds that happen to describe one. It
     * is asked per definition rather than per subtype because occlusion is a property
     * of the block and a subtype cannot differ from its siblings in it.
     */
    public static boolean fillsItsCube(BlockDef def) {
        return def.bounds()
            .flatMap(mod.xie.xm.content.model.MetaMap::first)
            .map(mod.xie.xm.content.model.Bounds::isFullBlock)
            .orElse(true);
    }

    /**
     * Properties for a {@code tree}: vanilla's own leaves, which the original's were
     * too.
     *
     * <p>{@code XieBlockLeaves} extended {@code BlockLeaves} and then set hardness
     * 0.2, a grass step sound and a light opacity of 1 — the three things 1.6's
     * {@code Material.leaves} did not already give it. Every one of those is in
     * {@code Blocks.leavesProperties} now, along with the handful that were block
     * flags in 2013: leaves do not occlude, do not suffocate, do not block sight,
     * and burn.
     *
     * <p>Hardness therefore defaults to vanilla's 0.2 rather than to zero, which is
     * the one place a tree differs from a {@code basic} block: a leaf block you
     * brush away instantly is not a leaf block.
     */
    public static BlockBehaviour.Properties forTree(BlockDef def) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
            .mapColor(mapColorFor(def))
            .sound(soundFor(def))
            .randomTicks()
            .noOcclusion()
            .isSuffocating((state, level, pos) -> false)
            .isRedstoneConductor((state, level, pos) -> false)
            // A full cube would otherwise be somewhere mobs spawn; vanilla leaves
            // admit the two that live in trees and nothing else.
            .isValidSpawn((state, level, pos, type) ->
                type == EntityTypes.OCELOT || type == EntityTypes.PARROT)
            .ignitedByLava()
            .pushReaction(PushReaction.POPPED);

        return common(def, properties, LEAF_HARDNESS);
    }

    /** {@code XieBlockLeaves} called {@code setHardness(0.2F)}, as vanilla still does. */
    private static final float LEAF_HARDNESS = 0.2f;

    /**
     * 1.6's {@code Block.getExplosionResistance}: {@code setResistance(r)} stored
     * {@code 3r} and the getter divided by 5, so a declared resistance is worth 0.6 of
     * itself now. Obsidian's 2000 is the 1200 it is today.
     */
    private static final float LEGACY_RESISTANCE_SCALE = 0.6f;

    /**
     * Strength and light level, which every archetype reads the same way.
     *
     * <p>{@code lightValue} is a fraction of full light, because 1.6's
     * {@code setLightValue(float)} stored {@code (int) (15 * f)}. {@code lightLevel}
     * is not light at all: it is the light a plant needs, read by
     * {@code XieRegistrar.plantConfig}.
     */
    private static BlockBehaviour.Properties common(BlockDef def,
                                                    BlockBehaviour.Properties properties,
                                                    float defaultHardness) {
        float hardness = def.props().getFloat("hardness", defaultHardness);
        float resistance = def.props().getFloat("resistance")
            .map(declared -> declared * LEGACY_RESISTANCE_SCALE)
            .orElse(hardness);
        properties = properties.strength(hardness, resistance);

        int light = (int) (15.0f * def.props().getFloat("lightValue", 0f));
        if (light > 0) {
            int clamped = Math.clamp(light, 0, 15);
            properties = properties.lightLevel(state -> clamped);
        }
        return properties;
    }

    /**
     * The {@code stepSound} property if given, otherwise inferred from
     * {@code material}, otherwise the grass default that plants used in 1.6.
     */
    public static SoundType soundFor(BlockDef def) {
        String sound = def.props().getString("stepSound")
            .or(() -> def.props().getString("stepsound"))
            .or(() -> def.props().getString("material"))
            .orElse("grass")
            .trim().toLowerCase(Locale.ROOT);

        return switch (sound) {
            case "powder", "sand" -> SoundType.SAND;
            case "wood" -> SoundType.WOOD;
            case "stone", "rock", "piston" -> SoundType.STONE;
            case "metal", "iron" -> SoundType.METAL;
            case "glass" -> SoundType.GLASS;
            case "cloth", "wool" -> SoundType.WOOL;
            case "gravel", "ground", "dirt" -> SoundType.GRAVEL;
            case "snow" -> SoundType.SNOW;
            case "ladder" -> SoundType.LADDER;
            case "slime" -> SoundType.SLIME_BLOCK;
            default -> SoundType.GRASS;
        };
    }

    /** Map colour inferred from {@code material}; plants default to green. */
    public static MapColor mapColorFor(BlockDef def) {
        String material = def.props().getString("material").orElse("").trim().toLowerCase(Locale.ROOT);
        return switch (material) {
            case "sand" -> MapColor.SAND;
            case "wood", "pumpkin" -> MapColor.WOOD;
            case "stone", "rock" -> MapColor.STONE;
            case "iron", "metal" -> MapColor.METAL;
            case "cloth", "wool" -> MapColor.WOOL;
            case "snow" -> MapColor.SNOW;
            case "water" -> MapColor.WATER;
            case "ground", "dirt" -> MapColor.DIRT;
            default -> MapColor.PLANT;
        };
    }
}
