package mod.xie.xm.content.model;

import java.util.List;

/**
 * One entry from a {@code "spawns"} section — where a block generates in the world.
 *
 * <p>Becomes a configured feature, a placed feature and a biome modifier on the
 * modern side. The parser keeps the placement rules as declared; translating
 * "spawns in these biomes, on these blocks, in clusters of N" into worldgen JSON
 * is the emitter's job.
 *
 * @param target       what generates
 * @param spawnsIn     blocks it may replace
 * @param spawnsOn     blocks it may sit on
 * @param spawnsUnder  blocks it must be beneath
 * @param spawnsNear   blocks it must be within {@code nearDistance} of
 * @param biomes       biome names or categories it is allowed in
 * @param props        remaining properties: clusterSize, rarity, nearDistance, ...
 */
public record SpawnDef(
    Origin origin,
    String name,
    ItemRef target,
    List<ItemRef> spawnsIn,
    List<ItemRef> spawnsOn,
    List<ItemRef> spawnsUnder,
    List<ItemRef> spawnsNear,
    List<String> biomes,
    PropertyMap props
) {

    public SpawnDef {
        spawnsIn = List.copyOf(spawnsIn);
        spawnsOn = List.copyOf(spawnsOn);
        spawnsUnder = List.copyOf(spawnsUnder);
        spawnsNear = List.copyOf(spawnsNear);
        biomes = List.copyOf(biomes);
    }

    public int clusterSize() {
        return props.peek("clustersize").flatMap(PropertyMap::asInt).orElse(1);
    }

    public int rarity() {
        return props.peek("rarity").flatMap(PropertyMap::asInt).orElse(1);
    }

    public int nearDistance() {
        return props.peek("neardistance").flatMap(PropertyMap::asInt).orElse(0);
    }

    public boolean hasPlacementRules() {
        return !spawnsIn.isEmpty() || !spawnsOn.isEmpty()
            || !spawnsUnder.isEmpty() || !spawnsNear.isEmpty();
    }
}
