package mod.xie.xm.content.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@code plantType}: the soil a plant will stand on, named rather than listed.
 *
 * <p>{@code Xie.parsePlantType} matched the word against Forge's
 * {@code EnumPlantType}, and Forge's {@code Block.canSustainPlant} turned the
 * constant into a soil test. It was the terse way to write a {@code growsOn} — one
 * word instead of a list — and it is the only thing {@code zombiePigmelonStem} says
 * about where it grows, which is what makes it a <em>nether</em> crop.
 *
 * <p>Five of the seven constants named a set of blocks and translate directly. The
 * other two named a condition rather than a set and have no place to go: {@code Cave}
 * asked whether the block below had a solid top face, and {@code Beach} asked for
 * dirt, grass or sand with water within one block. Nothing in the content declares
 * either, and a block tag cannot ask a question.
 */
public final class PlantTypes {

    private PlantTypes() { }

    /**
     * The blocks each named type stands on, as modern ids, from Forge 1.6's
     * {@code Block.canSustainPlant}. {@code Plains} listed grass and dirt, and
     * {@code Crop} tilled earth alone — vanilla wheat's own rule.
     */
    private static final Map<String, List<String>> SOILS = Map.of(
        "crop", List.of("minecraft:farmland"),
        "nether", List.of("minecraft:soul_sand"),
        "desert", List.of("minecraft:sand"),
        "plains", List.of("minecraft:grass_block", "minecraft:dirt"),
        "water", List.of("minecraft:water"));

    /** Types the original understood but which name a condition rather than a list. */
    private static final List<String> CONDITIONS = List.of("cave", "beach");

    /**
     * The blocks a {@code plantType} names, or empty when the word is one this cannot
     * express — either a condition or nothing the original knew at all.
     */
    public static Optional<List<String>> soilsFor(String plantType) {
        if (plantType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SOILS.get(plantType.trim().toLowerCase(Locale.ROOT)));
    }

    /** Whether this is one of the two that asked a question about the block below. */
    public static boolean isCondition(String plantType) {
        return plantType != null && CONDITIONS.contains(plantType.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * What a definition declaring no {@code plantType} meant.
     *
     * <p>{@code XieBlockStem.getPlantType} filled in {@code EnumPlantType.Crop} on
     * first ask, so a stem with neither property grows on farmland, exactly as
     * vanilla's melon stem does.
     */
    public static final String DEFAULT = "crop";
}
