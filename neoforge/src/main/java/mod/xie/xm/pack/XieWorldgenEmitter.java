package mod.xie.xm.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;

import mod.xie.xm.XiesMod;
import mod.xie.xm.content.model.BiomeNames;
import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.model.ItemRef;
import mod.xie.xm.content.model.ResourceNames;
import mod.xie.xm.content.model.SpawnDef;
import mod.xie.xm.reg.RegisteredContent;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns {@code spawns} declarations into worldgen JSON.
 *
 * <p>In 2013 this was a runtime system: {@code XieWorldGenerator} ran on every chunk
 * populate, walked a shuffled list of {@link mod.xie.xm.content.model.SpawnDef}-alikes
 * and hand-placed blocks. None of that has to exist now. Modern worldgen is
 * datapack-driven, so each entry becomes three files — a configured feature saying
 * <em>what</em>, a placed feature saying <em>where</em>, and a NeoForge biome modifier
 * saying <em>which biomes</em> — and vanilla's own generator runs them.
 *
 * <p>The mapping is closer than it looks, because vanilla's placement modifiers are
 * the same four questions {@code Spawnable} asked:
 *
 * <ul>
 *   <li>{@code rarity} is {@code random.nextInt(n) == 0} per chunk, which is exactly
 *       {@code rarity_filter};</li>
 *   <li>picking a column and taking the surface height is {@code in_square} then
 *       {@code heightmap};</li>
 *   <li>{@code clusterSize} scattered around that point is {@code count} then
 *       {@code random_offset}, which is how vanilla's own pumpkin patch is built;</li>
 *   <li>{@code spawnsOn}, {@code spawnsIn}, {@code spawnsUnder} and {@code spawnsNear}
 *       are one {@code block_predicate_filter} of offset block tests.</li>
 * </ul>
 *
 * <p>{@code clump} is the one that does not fit that shape. A clump was a contiguous
 * blob grown in square rings from a centre, not a scatter, so it becomes a
 * {@code disk} feature instead — and because a disk's {@code target} is itself a
 * block predicate, the placement rules move there and mean the same thing.
 *
 * <p>Every block named is resolved against the live registry first. The target has
 * to be a block <em>this mod</em> registered: an entry naming a vanilla block is
 * either content this build cannot make yet or, in the case of {@code melon}, content
 * dropped because vanilla now supplies it, and re-adding vanilla worldgen to biomes
 * is not what the engine is for. Those are counted and named rather than written out
 * as JSON that fails to load.
 */
public final class XieWorldgenEmitter {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** How the emitter resolves the block references in a spawn entry. */
    public interface BlockRefs {

        /**
         * A block this mod registered, for the thing being spawned. Empty for
         * anything else, including vanilla blocks.
         */
        Optional<String> modBlock(ItemRef ref);

        /** Any registered block, ours or vanilla, for a placement rule. */
        Optional<String> anyBlock(ItemRef ref);
    }

    /**
     * @param features placed features written, one per emitted entry
     * @param trees    tree configured features written, one per registered {@code tree}
     * @param skipped  entries not written, because something they name is unavailable
     */
    public record Counts(int features, int trees, int skipped) { }

    /** The block tag rather than the block: cave air and void air are air too. */
    private static final String AIR = "minecraft:air";

    /** What a tree may grow over as though it were air: {@code TreeFeature.validTreePos}. */
    private static final String TREE_ROOM = "#minecraft:replaceable_by_trees";

    /** The spread {@code Spawnable.attemptSpawn} used around a chosen point. */
    private static final int XZ_SPREAD = 8;
    private static final int Y_SPREAD = 2;

    /**
     * The largest {@code nearDistance} that gets expanded in full.
     *
     * <p>Vanilla has no "is one of these blocks within n" predicate, so the radius
     * becomes one offset test per position — {@code (2n+1)³} of them. That is 125 at
     * the distance the live content asks for and 2197 at {@code Spawnable}'s default
     * of six, which is more JSON than the rule is worth. Capping only ever makes the
     * test stricter, so a capped entry generates less often rather than wrongly.
     */
    private static final int MAX_NEAR_DISTANCE = 3;

    /** Plants go in with the flowers; a block replacing terrain goes in with the disks. */
    private static final String STEP_IN_AIR = "vegetal_decoration";
    private static final String STEP_IN_TERRAIN = "underground_ores";

    /** Where tree features go: a sapling needs them whether or not anything spawns. */
    private final Path dataRoot;

    /** Where everything else goes, in the worldgen pack a world can switch off. */
    private final Path spawnRoot;
    private final BlockRefs refs;

    /** Feature ids already written; spawn names repeat across files, as recipes do. */
    private final Set<String> usedIds = new HashSet<>();

    /** Leaves block id path to its tree, for the run in progress. */
    private Map<String, RegisteredContent.Tree> trees = Map.of();

    public XieWorldgenEmitter(Path dataRoot, Path spawnRoot, BlockRefs refs) {
        this.dataRoot = dataRoot;
        this.spawnRoot = spawnRoot;
        this.refs = refs;
    }

    public Counts emit(List<ContentPack> packs,
                       Map<String, RegisteredContent.Tree> trees) throws IOException {
        usedIds.clear();
        this.trees = new LinkedHashMap<>();

        int written = 0;
        List<String> skipped = new ArrayList<>();

        // Every tree gets its feature whether or not anything spawns it: a sapling
        // grows one by placing the same file, so a fruit tree that generates nowhere
        // in the world still has to be buildable by hand.
        for (Map.Entry<String, RegisteredContent.Tree> tree : trees.entrySet()) {
            // Unlike every other reference in a feature, a trunk is not checked
            // anywhere else: a "tree" definition names it and the registrar derives
            // the id it would have. A trunk block this build cannot register is
            // therefore a feature naming a block that does not exist, and a
            // configured feature naming a missing block does not fail quietly — it
            // fails the whole datapack load, which is a far worse answer than the
            // counted skip every other unported reference gets.
            String trunk = tree.getValue().trunkId();
            if (BuiltInRegistries.BLOCK.getOptional(Identifier.parse(trunk)).isEmpty()) {
                skipped.add(tree.getKey() + ": its trunk \"" + trunk + "\" is not a registered block");
                continue;
            }
            this.trees.put(tree.getKey(), tree.getValue());

            String path = RegisteredContent.treeFeaturePath(tree.getKey());
            usedIds.add(path);
            PackJson.write(dataRoot.resolve("worldgen").resolve("configured_feature")
                    .resolve(path + ".json"),
                treeFeature(XiesMod.MOD_ID + ":" + tree.getKey(), tree.getValue()));
        }

        for (ContentPack pack : packs) {
            for (SpawnDef spawn : pack.spawns()) {
                Optional<String> problem = write(spawn);
                if (problem.isEmpty()) {
                    written++;
                } else {
                    skipped.add(spawn.origin().qualifiedName() + ": " + problem.get());
                }
            }
        }

        if (!skipped.isEmpty()) {
            LOGGER.info("Worldgen not emitted for {} entries — {}",
                skipped.size(), String.join("; ", skipped));
        }
        return new Counts(written, this.trees.size(), skipped.size());
    }

    /** @return the reason this entry was skipped, or empty when it was written */
    private Optional<String> write(SpawnDef spawn) throws IOException {
        if (!spawn.generates()) {
            return Optional.of("rarity " + spawn.rarity() + " never generates");
        }
        if (!spawn.spawnType().equals("surface")) {
            return Optional.of("\"" + spawn.spawnType() + "\" generation is not ported");
        }

        Optional<String> spawned = refs.modBlock(spawn.target());
        if (spawned.isEmpty()) {
            return Optional.of("\"" + spawn.target() + "\" is not a block this build registers");
        }
        String blockId = spawned.get();

        List<String> biomes = biomes(spawn);
        if (biomes.isEmpty()) {
            return Optional.of("none of its biomes (" + String.join(", ", spawn.biomes())
                + ") has a modern equivalent");
        }

        // Each declared rule has to resolve to something. Dropping one entirely would
        // widen where the block generates rather than narrow it, which is the wrong
        // way to fail — the original abandoned the whole entry for the same reason.
        List<String> in = resolve(spawn, "spawnsIn", spawn.spawnsIn());
        List<String> on = resolve(spawn, "spawnsOn", spawn.spawnsOn());
        List<String> under = resolve(spawn, "spawnsUnder", spawn.spawnsUnder());
        List<String> near = resolve(spawn, "spawnsNear", spawn.spawnsNear());
        List<String> unresolved = unresolved(spawn, in, on, under, near);
        if (!unresolved.isEmpty()) {
            return Optional.of(String.join("; ", unresolved));
        }

        // A block that replaces terrain has to be placed one below the heightmap,
        // which reports the first free position above the ground rather than the
        // ground itself. A plant goes into that free position as it stands.
        boolean intoAir = in.isEmpty() || in.contains(AIR);
        int yShift = intoAir ? 0 : -1;

        String id = uniqueId(ResourceNames.toPath(spawn.name()));

        // A tree already has a feature — the one a sapling grows — so the entry
        // contributes only a placed feature pointing at it, and the feature refuses
        // a position it does not fit.
        String featureId = treeFeatureFor(blockId).orElse(null);

        // A plant or tree looks for ground beneath any canopy: otherwise the one column
        // an attempt gets lands on a forest's leaves about as often as on its floor,
        // and on a jungle's nearly every time. A tree also treats the plants it would
        // overgrow as room, because TreeFeature does. Water still stops the heightmap,
        // so either still needs dry land.
        JsonArray placement = new JsonArray();
        placement.add(rarityFilter(spawn.rarity()));
        placement.add(typed("minecraft:in_square"));
        placement.add(heightmap(intoAir ? "MOTION_BLOCKING_NO_LEAVES" : "OCEAN_FLOOR_WG"));
        placement.add(typed("minecraft:biome"));

        if (featureId != null) {
            scatter(placement, spawn, yShift);
            if (spawn.hasPlacementRules()) {
                List<String> room = new ArrayList<>(in);
                if (in.contains(AIR)) {
                    room.add(TREE_ROOM);
                }
                placement.add(blockPredicateFilter(
                    predicate(room, on, under, near, spawn.nearDistance(), Optional.empty())));
            }
        } else {
            JsonObject feature;
            if (spawn.clump()) {
                // The disk tests every column it covers, so the placement rules belong
                // to the feature rather than to the position it is placed at. Nothing
                // else checks whether the block can stay there, so "would it survive"
                // joins them — SimpleBlockFeature asks that itself, DiskFeature does not.
                JsonObject target = predicate(in, on, under, near, spawn.nearDistance(),
                    Optional.of(blockId));
                feature = disk(blockId, target, clumpRadius(spawn.clusterSize()));
                if (yShift != 0) {
                    placement.add(randomOffset(0, 0, yShift));
                }
            } else {
                feature = simpleBlock(blockId);
                scatter(placement, spawn, yShift);
                if (spawn.hasPlacementRules()) {
                    placement.add(blockPredicateFilter(
                        predicate(in, on, under, near, spawn.nearDistance(), Optional.empty())));
                }
            }
            featureId = XiesMod.MOD_ID + ":" + id;
            PackJson.write(spawnRoot.resolve("worldgen").resolve("configured_feature")
                .resolve(id + ".json"), feature);
        }

        JsonObject placed = new JsonObject();
        placed.addProperty("feature", featureId);
        placed.add("placement", placement);

        JsonObject modifier = new JsonObject();
        modifier.addProperty("type", "neoforge:add_features");
        modifier.add("biomes", biomeHolder(id, spawn));
        modifier.addProperty("features", XiesMod.MOD_ID + ":" + id);
        modifier.addProperty("step", intoAir ? STEP_IN_AIR : STEP_IN_TERRAIN);

        Path worldgen = spawnRoot.resolve("worldgen");
        PackJson.write(worldgen.resolve("placed_feature").resolve(id + ".json"), placed);
        PackJson.write(spawnRoot.resolve("neoforge").resolve("biome_modifier").resolve(id + ".json"),
            modifier);
        return Optional.empty();
    }

    /**
     * The cluster spread: {@code clusterSize} positions around the chosen column,
     * which is {@code Spawnable.attemptSpawn}'s own. A single block needs none, and
     * without it the heightmap position is exact rather than something the predicate
     * has to filter.
     */
    private static void scatter(JsonArray placement, SpawnDef spawn, int yShift) {
        int count = Math.max(1, spawn.clusterSize());
        if (count > 1) {
            placement.add(count(count));
            placement.add(randomOffset(XZ_SPREAD, Y_SPREAD, yShift));
        } else if (yShift != 0) {
            placement.add(randomOffset(0, 0, yShift));
        }
    }

    /** The tree feature a block id names, when that block is a {@code tree}. */
    private Optional<String> treeFeatureFor(String blockId) {
        String path = blockId.substring(blockId.indexOf(':') + 1);
        return trees.containsKey(path)
            ? Optional.of(XiesMod.MOD_ID + ":" + RegisteredContent.treeFeaturePath(path))
            : Optional.empty();
    }

    // ------------------------------------------------------------- resolution

    /** One declared block list as registered ids, in order and without duplicates. */
    private List<String> resolve(SpawnDef spawn, String rule, List<ItemRef> declared) {
        Set<String> ids = new LinkedHashSet<>();
        for (ItemRef ref : declared) {
            refs.anyBlock(ref).ifPresentOrElse(ids::add, () ->
                LOGGER.debug("Spawn {} lists \"{}\" under {}, which is not a registered block",
                    spawn.origin(), ref, rule));
        }
        return List.copyOf(ids);
    }

    /** The names of any rule that was declared but resolved to nothing at all. */
    private List<String> unresolved(SpawnDef spawn, List<String> in, List<String> on,
                                    List<String> under, List<String> near) {
        List<String> problems = new ArrayList<>();
        if (in.isEmpty() && !spawn.spawnsIn().isEmpty()) {
            problems.add("nothing in spawnsIn is a registered block");
        }
        if (on.isEmpty() && !spawn.spawnsOn().isEmpty()) {
            problems.add("nothing in spawnsOn is a registered block");
        }
        if (under.isEmpty() && !spawn.spawnsUnder().isEmpty()) {
            problems.add("nothing in spawnsUnder is a registered block");
        }
        if (near.isEmpty() && !spawn.spawnsNear().isEmpty()) {
            problems.add("nothing in spawnsNear is a registered block");
        }
        return problems;
    }

    /**
     * The {@code biomes} field of the modifier: one biome reference.
     *
     * <p>A holder set is written either as a single tag or as a list of concrete ids,
     * and a list of <em>tags</em> is not one of its forms — which is a problem, since
     * a legacy entry naming three biome words usually means three tags. So several
     * references become a biome tag of this mod's own, listing them; a tag file may
     * contain other tags even where a holder set may not.
     *
     * @return the reference to write, having written the tag first if it needed one
     */
    private String biomeSet(String id, List<String> biomes) throws IOException {
        if (biomes.size() == 1) {
            return biomes.get(0);
        }
        JsonArray values = new JsonArray();
        biomes.forEach(values::add);
        JsonObject tag = new JsonObject();
        tag.add("values", values);

        String path = "spawns_in/" + id;
        PackJson.write(spawnRoot.resolve("tags").resolve("worldgen").resolve("biome")
            .resolve(path + ".json"), tag);
        return "#" + XiesMod.MOD_ID + ":" + path;
    }

    /**
     * The {@code biomes} field of the modifier, including any biomes a word
     * {@linkplain BiomeNames#excluded leaves out}.
     *
     * <p>A tag only ever adds, so an excluding word cannot join the others in
     * {@link #biomeSet}'s tag. It becomes NeoForge's composite holder set instead —
     * its tag {@code and} {@code not} each exclusion — joined to the rest with
     * {@code or}.
     */
    private JsonElement biomeHolder(String id, SpawnDef spawn) throws IOException {
        Set<String> plain = new LinkedHashSet<>();
        Set<JsonElement> narrowed = new LinkedHashSet<>();
        if (spawn.biomes().isEmpty()) {
            plain.add(BiomeNames.ANY_OVERWORLD);
        }
        for (String declared : spawn.biomes()) {
            List<String> excluded = BiomeNames.excluded(declared);
            if (excluded.isEmpty()) {
                plain.addAll(BiomeNames.modern(declared));
                continue;
            }
            JsonArray values = new JsonArray();
            BiomeNames.modern(declared).forEach(values::add);
            for (String out : excluded) {
                JsonObject not = typed("neoforge:not");
                // A single id is wrapped, because only a tag may stand alone everywhere.
                if (out.startsWith("#")) {
                    not.addProperty("value", out);
                } else {
                    JsonArray ids = new JsonArray();
                    ids.add(out);
                    not.add("value", ids);
                }
                values.add(not);
            }
            JsonObject and = typed("neoforge:and");
            and.add("values", values);
            narrowed.add(and);
        }

        List<JsonElement> parts = new ArrayList<>();
        if (!plain.isEmpty()) {
            parts.add(new JsonPrimitive(biomeSet(id, List.copyOf(plain))));
        }
        parts.addAll(narrowed);
        if (parts.size() == 1) {
            return parts.get(0);
        }
        JsonArray values = new JsonArray();
        parts.forEach(values::add);
        JsonObject or = typed("neoforge:or");
        or.add("values", values);
        return or;
    }

    /** The modern biome references this entry allows, or everything when it says nothing. */
    private List<String> biomes(SpawnDef spawn) {
        if (spawn.biomes().isEmpty()) {
            return List.of(BiomeNames.ANY_OVERWORLD);
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (String declared : spawn.biomes()) {
            List<String> modern = BiomeNames.modern(declared);
            if (modern.isEmpty()) {
                LOGGER.debug("Spawn {} names the biome \"{}\", which has no modern equivalent",
                    spawn.origin(), declared);
            }
            allowed.addAll(modern);
        }
        return List.copyOf(allowed);
    }

    // -------------------------------------------------------------- predicates

    /**
     * The four placement rules as one predicate, optionally with the block's own
     * "could this survive here" test folded in for a feature that does not ask.
     */
    private JsonObject predicate(List<String> in, List<String> on, List<String> under,
                                 List<String> near, int nearDistance,
                                 Optional<String> survives) {
        JsonArray parts = new JsonArray();
        if (!in.isEmpty()) {
            parts.add(matching(in, 0, 0, 0));
        }
        if (!on.isEmpty()) {
            parts.add(matching(on, 0, -1, 0));
        }
        if (!under.isEmpty()) {
            parts.add(matching(under, 0, 1, 0));
        }
        if (!near.isEmpty()) {
            parts.add(within(near, nearDistance));
        }
        survives.map(XieWorldgenEmitter::wouldSurvive).ifPresent(parts::add);
        return combine("minecraft:all_of", parts);
    }

    /**
     * "One of these blocks is at this offset".
     *
     * <p>Air is tested through its tag rather than by name, because cave air and void
     * air are air as far as anything placing a block is concerned, and vanilla's own
     * patch features test it that way. Any other {@code #}-prefixed id is a tag too.
     */
    private JsonObject matching(List<String> ids, int dx, int dy, int dz) {
        List<String> concrete = ids.stream()
            .filter(id -> !id.equals(AIR) && !id.startsWith("#")).toList();
        JsonArray parts = new JsonArray();
        for (String id : ids) {
            if (id.equals(AIR) || id.startsWith("#")) {
                JsonObject tag = new JsonObject();
                tag.addProperty("type", "minecraft:matching_block_tag");
                tag.addProperty("tag", id.startsWith("#") ? id.substring(1) : id);
                offset(tag, dx, dy, dz);
                parts.add(tag);
            }
        }
        if (!concrete.isEmpty()) {
            JsonObject blocks = new JsonObject();
            blocks.addProperty("type", "minecraft:matching_blocks");
            blocks.add("blocks", oneOrMany(concrete));
            offset(blocks, dx, dy, dz);
            parts.add(blocks);
        }
        return combine("minecraft:any_of", parts);
    }

    /**
     * "One of these blocks is somewhere within {@code distance}".
     *
     * <p>{@code Spawnable.canSpawnHere} scanned a cube for this, and there is no
     * predicate that takes a radius, so the cube is written out as one offset test per
     * position. They are ordered nearest first, since a match short-circuits the rest.
     *
     * <p>The 2013 loop is not what is reproduced here: its three nested loops all
     * increment and test the same variable, so it walks a line along x with y and z
     * pinned at the corner instead of filling the cube. The cube is plainly what was
     * meant, and it is the second time a formula in that file has had to be read for
     * intent rather than copied.
     */
    private JsonObject within(List<String> ids, int distance) {
        int radius = Math.min(distance, MAX_NEAR_DISTANCE);
        if (radius < distance) {
            LOGGER.debug("Clamping a nearDistance of {} to {}: the search is written out as one "
                + "test per position, and {} of them is more than the rule is worth",
                distance, MAX_NEAR_DISTANCE, (2 * distance + 1) * (2 * distance + 1) * (2 * distance + 1));
        }

        List<int[]> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    offsets.add(new int[] {dx, dy, dz});
                }
            }
        }
        offsets.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1] + o[2] * o[2]));

        JsonArray parts = new JsonArray();
        for (int[] at : offsets) {
            parts.add(matching(ids, at[0], at[1], at[2]));
        }
        return combine("minecraft:any_of", parts);
    }

    private static JsonObject wouldSurvive(String blockId) {
        JsonObject predicate = new JsonObject();
        predicate.addProperty("type", "minecraft:would_survive");
        predicate.add("state", blockState(blockId));
        return predicate;
    }

    /** Wraps parts in {@code all_of}/{@code any_of}, unless there is only one. */
    private static JsonObject combine(String type, JsonArray parts) {
        if (parts.isEmpty()) {
            return typed("minecraft:true");
        }
        if (parts.size() == 1) {
            return parts.get(0).getAsJsonObject();
        }
        JsonObject combined = new JsonObject();
        combined.addProperty("type", type);
        combined.add("predicates", parts);
        return combined;
    }

    private static void offset(JsonObject predicate, int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }
        JsonArray at = new JsonArray();
        at.add(dx);
        at.add(dy);
        at.add(dz);
        predicate.add("offset", at);
    }

    // ---------------------------------------------------------------- features

    private static JsonObject simpleBlock(String blockId) {
        JsonObject config = new JsonObject();
        config.add("to_place", stateProvider(blockId));

        JsonObject feature = new JsonObject();
        feature.addProperty("type", "minecraft:simple_block");
        feature.add("config", config);
        return feature;
    }

    private static JsonObject disk(String blockId, JsonObject target, int radius) {
        JsonObject config = new JsonObject();
        config.add("state_provider", stateProvider(blockId));
        config.add("target", target);
        config.addProperty("radius", radius);
        // One layer. A clump was flat: it grew outwards in x and z from one point.
        config.addProperty("half_height", 0);

        JsonObject feature = new JsonObject();
        feature.addProperty("type", "minecraft:disk");
        feature.add("config", config);
        return feature;
    }

    /**
     * The radius of a disk holding about {@code clusterSize} blocks, using the
     * original's own arithmetic: {@code Spawnable.attemptSpawn} grew square rings out
     * to {@code ceil((sqrt(n) - 1) / 2)}. It gives 1, 2 and 3 for the cluster sizes
     * the live content asks for, which is 5, 13 and 29 blocks against the 5, 16 and
     * 36 declared — the same shape one ring tighter, because a disk is round.
     */
    private static int clumpRadius(int clusterSize) {
        int radius = (int) Math.ceil((Math.sqrt(Math.max(1, clusterSize)) - 1) / 2);
        // The codec caps a disk at eight, which is far past anything content declares.
        return Math.clamp(radius, 0, 8);
    }

    // -------------------------------------------------------------------- trees

    /**
     * A {@code tree} archetype as a {@code minecraft:tree} feature.
     *
     * <p>This is the whole of {@code TreeGenerator}, and the reason it does not need
     * porting. Its three generators were 1.6's oak, spruce and pine copied out of
     * vanilla, and all three survive as configurations of this feature — so what
     * would have been several hundred lines of block placement is the difference
     * between three trunk placers and three foliage placers, and vanilla's own
     * {@code TreeFeature} does the placing.
     *
     * <p>The numbers are read straight off {@code data/minecraft/worldgen/
     * configured_feature/{oak,spruce,pine}.json} rather than translated from the 2013
     * code, because the 2013 code <em>is</em> those trees: {@code generateOak} rolls
     * {@code nextInt(3) + 4} for its trunk and {@code straight_trunk_placer} with a
     * base of 4 and a random of 2 rolls exactly the same 4 to 6.
     */
    private static JsonObject treeFeature(String leavesId, RegisteredContent.Tree tree) {
        JsonObject config = new JsonObject();
        config.add("trunk_provider", axisProvider(tree.trunkId()));
        config.add("foliage_provider", tree.fruitAges() > 0
            ? fruitingFoliage(leavesId, tree.fruitAges())
            : stateProvider(leavesId));
        config.add("decorators", new JsonArray());

        switch (tree.shape()) {
            case CONIFER -> {
                config.add("trunk_placer", straightTrunk(5, 2, 1));
                JsonObject foliage = typed("minecraft:spruce_foliage_placer");
                foliage.add("trunk_height", uniform(1, 2));
                foliage.add("radius", uniform(2, 3));
                foliage.add("offset", uniform(0, 2));
                config.add("foliage_placer", foliage);
                config.add("minimum_size", twoLayers(2, 0, 2));
            }
            case TALL -> {
                config.add("trunk_placer", straightTrunk(6, 4, 0));
                JsonObject foliage = typed("minecraft:pine_foliage_placer");
                foliage.add("height", uniform(3, 4));
                foliage.addProperty("offset", 1);
                foliage.addProperty("radius", 1);
                config.add("foliage_placer", foliage);
                config.add("minimum_size", twoLayers(2, 0, 2));
            }
            default -> {
                config.add("trunk_placer", straightTrunk(4, 2, 0));
                JsonObject foliage = typed("minecraft:blob_foliage_placer");
                foliage.addProperty("radius", 2);
                foliage.addProperty("offset", 0);
                foliage.addProperty("height", 3);
                config.add("foliage_placer", foliage);
                config.add("minimum_size", twoLayers(1, 0, 1));
            }
        }

        JsonObject feature = new JsonObject();
        feature.addProperty("type", "minecraft:tree");
        feature.add("config", config);
        return feature;
    }

    private static JsonObject straightTrunk(int baseHeight, int randA, int randB) {
        JsonObject placer = typed("minecraft:straight_trunk_placer");
        placer.addProperty("base_height", baseHeight);
        placer.addProperty("height_rand_a", randA);
        placer.addProperty("height_rand_b", randB);
        return placer;
    }

    private static JsonObject twoLayers(int limit, int lowerSize, int upperSize) {
        JsonObject size = typed("minecraft:two_layers_feature_size");
        size.addProperty("limit", limit);
        size.addProperty("lower_size", lowerSize);
        size.addProperty("upper_size", upperSize);
        return size;
    }

    /** How many bare leaves a fruiting canopy generates for each one already ripe. */
    private static final int BARE_LEAVES_PER_RIPE = 9;

    /**
     * The leaves of a tree that bears fruit: mostly bare, with one in
     * {@value #BARE_LEAVES_PER_RIPE} plus one already ripe — or, on a tree with more
     * than one ripeness stage, one at each later stage.
     *
     * <p>Vanilla's sweet berry patches generate at full age so that a bush found in a
     * taiga has berries on it, and a fruit tree found in a forest should likewise have
     * some fruit on it. The same feature places the tree a sapling grows, so a planted
     * tree comes up with a few as well; the rest ripen on their own. Leaves inside the
     * canopy draw a ripe state too, but neither show it nor hold a neighbour bare: the
     * ripening rule in {@code XieTreeBlock} counts only a ripe neighbour that is open
     * to the air, and is about what grows, not what was placed.
     */
    private static JsonObject fruitingFoliage(String leavesId, int maxAge) {
        JsonArray entries = new JsonArray();
        for (int age = 0; age <= maxAge; age++) {
            JsonObject properties = new JsonObject();
            properties.addProperty("age", String.valueOf(age));
            JsonObject state = blockState(leavesId);
            state.add("Properties", properties);

            JsonObject entry = new JsonObject();
            entry.addProperty("weight", age == 0 ? BARE_LEAVES_PER_RIPE : 1);
            entry.add("data", state);
            entries.add(entry);
        }
        JsonObject provider = typed("minecraft:weighted_state_provider");
        provider.add("entries", entries);
        return provider;
    }

    /** A log, which unlike a leaf has to say which way up it is. */
    private static JsonObject axisProvider(String blockId) {
        JsonObject properties = new JsonObject();
        properties.addProperty("axis", "y");

        JsonObject state = blockState(blockId);
        state.add("Properties", properties);

        JsonObject provider = new JsonObject();
        provider.addProperty("type", "minecraft:simple_state_provider");
        provider.add("state", state);
        return provider;
    }

    private static JsonObject stateProvider(String blockId) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "minecraft:simple_state_provider");
        provider.add("state", blockState(blockId));
        return provider;
    }

    private static JsonObject blockState(String blockId) {
        JsonObject state = new JsonObject();
        state.addProperty("Name", blockId);
        return state;
    }

    // -------------------------------------------------------------- placement

    private static JsonObject typed(String type) {
        JsonObject modifier = new JsonObject();
        modifier.addProperty("type", type);
        return modifier;
    }

    private static JsonObject rarityFilter(int chance) {
        JsonObject modifier = typed("minecraft:rarity_filter");
        modifier.addProperty("chance", chance);
        return modifier;
    }

    private static JsonObject heightmap(String type) {
        JsonObject modifier = typed("minecraft:heightmap");
        modifier.addProperty("heightmap", type);
        return modifier;
    }

    private static JsonObject count(int count) {
        JsonObject modifier = typed("minecraft:count");
        modifier.addProperty("count", count);
        return modifier;
    }

    /**
     * Scatters each position, shifting it vertically by {@code yShift} first. The
     * original drew both offsets from a flat distribution, so this does too, rather
     * than the trapezoid vanilla's own patches favour.
     */
    private static JsonObject randomOffset(int xzSpread, int ySpread, int yShift) {
        JsonObject modifier = typed("minecraft:random_offset");
        if (xzSpread > 0) {
            modifier.add("xz_spread", uniform(-xzSpread, xzSpread));
        } else {
            modifier.addProperty("xz_spread", 0);
        }
        if (ySpread > 0) {
            modifier.add("y_spread", uniform(yShift - ySpread, yShift + ySpread));
        } else {
            modifier.addProperty("y_spread", yShift);
        }
        return modifier;
    }

    private static JsonObject uniform(int min, int max) {
        JsonObject provider = typed("minecraft:uniform");
        provider.addProperty("min_inclusive", min);
        provider.addProperty("max_inclusive", max);
        return provider;
    }

    private static JsonObject blockPredicateFilter(JsonObject predicate) {
        JsonObject modifier = typed("minecraft:block_predicate_filter");
        modifier.add("predicate", predicate);
        return modifier;
    }

    // ---------------------------------------------------------------- plumbing

    /** A one-element holder set serialises as the bare id; more than one as a list. */
    private static JsonElement oneOrMany(List<String> ids) {
        if (ids.size() == 1) {
            return new JsonPrimitive(ids.get(0));
        }
        JsonArray array = new JsonArray();
        ids.forEach(array::add);
        return array;
    }

    private String uniqueId(String base) {
        if (usedIds.add(base)) {
            return base;
        }
        for (int n = 2; ; n++) {
            String candidate = base + "_" + n;
            if (usedIds.add(candidate)) {
                return candidate;
            }
        }
    }
}
