package mod.xie.xm.content.model;

import java.util.List;
import java.util.Locale;

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

    /** How many blocks one successful spawn places. {@code Spawnable.clusterSizeMax}. */
    public int clusterSize() {
        return props.getInt("clustersize", 1);
    }

    /**
     * One in {@code rarity} chunks gets an attempt — {@code Spawnable.spawnFactor},
     * rolled as {@code random.nextInt(spawnFactor) == 0}. Larger is rarer, and the
     * default of 15 is the field's initialiser rather than anything the format says.
     *
     * <p>Zero or less never generates at all, which is how content switches an entry
     * off without deleting it.
     */
    public int rarity() {
        return props.getInt("rarity", 15);
    }

    /** Whether {@link #rarity} allows this entry to generate at all. */
    public boolean generates() {
        return rarity() > 0;
    }

    /**
     * How far {@link #spawnsNear} may look, in blocks. Six is
     * {@code Spawnable.nearDistance}'s initialiser; the original ignored a declared
     * value of zero or less rather than treating it as "must be at this very block".
     */
    public int nearDistance() {
        int declared = props.getInt("neardistance", 0);
        return declared > 0 ? declared : 6;
    }

    /**
     * Whether a cluster is one contiguous blob rather than a scatter.
     * {@code Spawnable.attemptSpawn} builds a clump as square rings around a centre
     * and a scatter as repeated random offsets from one point.
     */
    public boolean clump() {
        return props.getBoolean("clump", false);
    }

    /**
     * Which generation pass this belongs to: {@code surface}, {@code nether},
     * {@code dungeon} or {@code ore}, defaulting to {@code surface} as
     * {@code JobSpawn} did.
     */
    public String spawnType() {
        return props.getString("type")
            .map(text -> text.trim().toLowerCase(Locale.ROOT))
            .filter(text -> !text.isEmpty())
            .orElse("surface");
    }

    public boolean hasPlacementRules() {
        return !spawnsIn.isEmpty() || !spawnsOn.isEmpty()
            || !spawnsUnder.isEmpty() || !spawnsNear.isEmpty();
    }
}
