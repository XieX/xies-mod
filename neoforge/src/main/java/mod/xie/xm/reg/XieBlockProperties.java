package mod.xie.xm.reg;

import mod.xie.xm.content.model.BlockDef;

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

        float hardness = def.props().getFloat("hardness", 0.15f);
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
