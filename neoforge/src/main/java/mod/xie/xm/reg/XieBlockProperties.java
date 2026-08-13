package mod.xie.xm.reg;

import mod.xie.xm.content.model.BlockDef;

import net.minecraft.world.entity.EntityType;
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

    /** Properties for a plant-like block: no collision, ticks, breaks instantly-ish. */
    public static BlockBehaviour.Properties forPlant(BlockDef def) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
            .mapColor(mapColorFor(def))
            .sound(soundFor(def))
            .noCollision()
            .randomTicks()
            .pushReaction(PushReaction.DESTROY);

        return common(def, properties, 0.15f);
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
            properties = properties.noCollision().pushReaction(PushReaction.DESTROY);
        }
        if (!fillsItsCube(def)) {
            // XieBlock.setBlockBounds called setIsOpaque(false) whenever the box was
            // not the whole cube, whatever else the definition said.
            properties = properties.noOcclusion();
        }

        return common(def, properties, 0f);
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
            .pushReaction(PushReaction.DESTROY);

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
            .pushReaction(PushReaction.DESTROY);
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
            .isViewBlocking((state, level, pos) -> false)
            .isRedstoneConductor((state, level, pos) -> false)
            // A full cube would otherwise be somewhere mobs spawn; vanilla leaves
            // admit the two that live in trees and nothing else.
            .isValidSpawn((state, level, pos, type) ->
                type == EntityType.OCELOT || type == EntityType.PARROT)
            .ignitedByLava()
            .pushReaction(PushReaction.DESTROY);

        return common(def, properties, LEAF_HARDNESS);
    }

    /** {@code XieBlockLeaves} called {@code setHardness(0.2F)}, as vanilla still does. */
    private static final float LEAF_HARDNESS = 0.2f;

    /** Strength and light level, which every archetype reads the same way. */
    private static BlockBehaviour.Properties common(BlockDef def,
                                                    BlockBehaviour.Properties properties,
                                                    float defaultHardness) {
        float hardness = def.props().getFloat("hardness", defaultHardness);
        float resistance = def.props().getFloat("resistance", hardness);
        properties = properties.strength(hardness, resistance);

        int light = def.props().getInt("lightValue", def.props().getInt("lightLevel", 0));
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
            case "stone", "piston" -> SoundType.STONE;
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
