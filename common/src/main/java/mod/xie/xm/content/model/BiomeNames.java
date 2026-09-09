package mod.xie.xm.content.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps the biome words a {@code spawns} entry lists onto modern biome references.
 *
 * <p>1.6 had no biome ids and no biome tags. {@code Spawnable.biomeCheck} lowercased
 * the biome's <em>display name</em> and asked whether it contained the declared word,
 * so {@code "biomes": "hills"} caught ExtremeHills, ForestHills and DesertHills at
 * once, and caught a third-party mod's hills too without either side knowing about
 * the other.
 *
 * <p>That is what a biome tag is for, so a word maps to a tag wherever one exists.
 * NeoForge's {@code c:} tags are preferred over the vanilla {@code minecraft:} ones
 * because they are the set other mods also populate, which keeps the original's
 * accidental cross-mod reach: a biome mod that tags its swamp {@code c:is_swamp}
 * gets wild tomatoes without anyone editing this table. Where no tag fits, a word
 * maps to concrete biome ids instead.
 *
 * <p>Two words have no modern counterpart of their own. {@code marsh} and
 * {@code shrubland} were biomes from the mod packs of the day, not from vanilla, and
 * nothing tags anything as either. Each takes its nearest neighbour instead: a marsh
 * is a wetland, so it joins the swamps, and a shrubland is dry open scrub, which is
 * what a savanna is. A word this table has no row for at all still resolves to
 * nothing, and the caller reports it rather than silently widening the biome list.
 */
public final class BiomeNames {

    /** Every biome the overworld has, for an entry that names none. */
    public static final String ANY_OVERWORLD = "#minecraft:is_overworld";

    private static final Map<String, List<String>> BIOMES = new LinkedHashMap<>();

    private BiomeNames() { }

    private static void map(String legacy, String... modern) {
        BIOMES.put(legacy, List.of(modern));
    }

    static {
        map("plains", "#c:is_plains");
        map("meadow", "minecraft:meadow");
        map("savanna", "#c:is_savanna");
        map("shrubland", "#c:is_savanna");
        map("forest", "#c:is_forest");
        map("wood", "#c:is_forest");
        map("woods", "#c:is_forest");
        map("woodland", "#c:is_forest");
        map("foresthills", "minecraft:windswept_forest");
        map("jungle", "#c:is_jungle");
        map("junglehills", "#c:is_jungle");
        map("swamp", "#c:is_swamp");
        map("swampland", "#c:is_swamp");
        map("marsh", "#c:is_swamp");
        map("desert", "#c:is_desert");
        map("deserthills", "#c:is_desert");
        map("taiga", "#c:is_taiga");
        map("taigahills", "#c:is_taiga");
        map("hills", "#c:is_hill");
        map("extremehills", "#c:is_hill");
        map("mountain", "#c:is_mountain");
        map("mountains", "#c:is_mountain");
        map("beach", "#c:is_beach");
        map("ocean", "#c:is_ocean");
        map("river", "#c:is_river");
        map("badlands", "#c:is_badlands");
        map("mesa", "#c:is_badlands");
        map("mushroom", "#c:is_mushroom");
        map("mushroomisland", "#c:is_mushroom");
        map("ice", "#c:is_icy");
        map("iceplains", "minecraft:snowy_plains");
        map("frozen", "#c:is_icy");
        map("snow", "#c:is_snowy");
        map("snowy", "#c:is_snowy");
        map("jungleedge", "minecraft:sparse_jungle");
        map("nether", "#c:is_nether");
        map("hell", "#c:is_nether");
        map("sky", "#c:is_end");
        map("end", "#c:is_end");
    }

    /**
     * The modern references one declared biome word stands for, or an empty list
     * when nothing modern corresponds to it.
     *
     * <p>Each is either a tag ({@code #c:is_swamp}) or a biome id
     * ({@code minecraft:meadow}), both of which a NeoForge biome modifier's
     * {@code biomes} field accepts.
     */
    public static List<String> modern(String legacyName) {
        if (legacyName == null) {
            return List.of();
        }
        String key = legacyName.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        return BIOMES.getOrDefault(key, List.of());
    }
}
