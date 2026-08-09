package mod.xie.xm.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import mod.xie.xm.XiesMod;
import mod.xie.xm.content.model.AliasDef;
import mod.xie.xm.content.model.BlockDef;
import mod.xie.xm.content.model.ContentPack;
import mod.xie.xm.content.model.DropTable;
import mod.xie.xm.content.model.ItemDef;
import mod.xie.xm.content.model.ItemRef;
import mod.xie.xm.content.model.RecipeDef;
import mod.xie.xm.content.model.ResourceNames;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes the generated datapack: loot tables from {@code drops}, and recipes.
 *
 * <p>This is where the port earns the most against the 2013 engine. The original
 * rolled its own drop logic ({@code Droppables}) and registered recipes directly
 * with {@code CraftingManager}; here both become JSON that vanilla's own loaders
 * consume, so fortune, explosion decay, the recipe book and datapack overrides all
 * come for free.
 *
 * <p>The single largest reduction is aliases. {@code $salady} used to be expanded
 * into the cartesian product of concrete recipes at load time — one sammich recipe
 * with three alias slots over six-item aliases became 216 registered recipes.
 * Modern Minecraft has item tags, so an alias becomes one tag and the recipe stays
 * one recipe.
 *
 * <p>References are resolved against the live registries rather than trusted
 * blindly. Most of the content is not registered yet — only a few archetypes are
 * ported — so a recipe naming an item this build cannot make would otherwise write
 * JSON that fails to load and buries the real errors. Anything unresolvable is
 * dropped here, counted, and reported.
 */
public final class XieDataEmitter {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Whether a namespaced id names an item that actually exists in the registry. */
    @FunctionalInterface
    public interface ItemLookup {
        boolean exists(String id);
    }

    /** Vanilla's crafting grid; anything larger was rejected in 2013 too. */
    private static final int MAX_GRID = RecipeDef.MAX_GRID;

    /** Furnace defaults, since {@code .xie} smelting declares neither. */
    private static final int SMELT_TIME = 200;
    private static final float SMELT_EXPERIENCE = 0.1f;

    /**
     * What one call to {@link #emit} produced.
     *
     * @param lootTables    block loot tables written
     * @param recipes       recipes written
     * @param tags          alias item tags written
     * @param skippedDrops  drops whose item could not be resolved
     * @param skippedRecipes recipes dropped because something in them is not ported
     * @param culls         {@code cull} recipes, which have no modern equivalent
     */
    public record Counts(int lootTables, int recipes, int tags,
                         int skippedDrops, int skippedRecipes, int culls) { }

    private final Path dataRoot;
    private final ItemLookup lookup;

    /** Every name any pack declares, so a reference can be told from a vanilla one. */
    private final Set<String> declaredNames = new HashSet<>();

    /** Declared names that replace a vanilla object rather than adding a new one. */
    private final Set<String> overriddenNames = new HashSet<>();

    /** Alias name to the tag reference it became, e.g. {@code salady} to {@code #xiesmod:salady}. */
    private final Map<String, String> aliasTags = new LinkedHashMap<>();

    /** Recipe ids already written; names repeat across files and must not collide. */
    private final Set<String> usedRecipeIds = new HashSet<>();

    public XieDataEmitter(Path outputRoot, ItemLookup lookup) {
        this.dataRoot = outputRoot.resolve("data").resolve(XiesMod.MOD_ID);
        this.lookup = lookup;
    }

    /**
     * @param packs        the loaded content
     * @param blockMaxAges definition name to highest age; only registered blocks appear
     */
    public Counts emit(List<ContentPack> packs, Map<String, Integer> blockMaxAges) throws IOException {
        declaredNames.clear();
        overriddenNames.clear();
        aliasTags.clear();
        usedRecipeIds.clear();

        for (ContentPack pack : packs) {
            declaredNames.addAll(pack.blocks().keySet());
            declaredNames.addAll(pack.items().keySet());
            pack.blocks().values().stream().filter(BlockDef::isOverride)
                .forEach(def -> overriddenNames.add(def.name()));
            pack.items().values().stream().filter(ItemDef::isOverride)
                .forEach(def -> overriddenNames.add(def.name()));
        }

        int tags = writeAliasTags(packs);

        int lootTables = 0;
        int skippedDrops = 0;
        int recipes = 0;
        int culls = 0;
        List<String> skippedRecipes = new ArrayList<>();

        for (ContentPack pack : packs) {
            for (BlockDef def : pack.blocks().values()) {
                Integer maxAge = blockMaxAges.get(def.name());
                if (maxAge == null || def.drops().isEmpty()) {
                    continue;
                }
                LootResult result = writeLootTable(def, maxAge);
                lootTables += result.written() ? 1 : 0;
                skippedDrops += result.skippedDrops();
            }

            for (RecipeDef recipe : pack.recipes()) {
                if (recipe.kind() == RecipeDef.Kind.CULL) {
                    culls++;
                    LOGGER.debug("Skipping cull recipe {}: removing vanilla recipes has no "
                        + "datapack equivalent", recipe.origin());
                    continue;
                }
                int written = writeRecipe(recipe);
                if (written > 0) {
                    recipes += written;
                } else {
                    skippedRecipes.add(recipe.origin().qualifiedName());
                }
            }
        }

        // Named, not just counted. A recipe that quietly fails to appear is the
        // hardest thing to diagnose while archetypes are still being ported, and
        // the reason for each one is a debug line away once you know which to look
        // for.
        if (!skippedRecipes.isEmpty()) {
            LOGGER.info("Recipes not emitted, because something they name is not registered yet: {}",
                String.join(", ", skippedRecipes));
        }

        return new Counts(lootTables, recipes, tags, skippedDrops, skippedRecipes.size(), culls);
    }

    // ----------------------------------------------------------- loot tables

    private record LootResult(boolean written, int skippedDrops) { }

    /**
     * One loot table per block, at the id vanilla derives from the block's own:
     * {@code xiesmod:blocks/<path>}, so nothing has to be wired up on the block.
     */
    private LootResult writeLootTable(BlockDef def, int maxAge) throws IOException {
        String path = ResourceNames.toPath(def.name());
        String blockId = XiesMod.MOD_ID + ":" + path;

        JsonArray pools = new JsonArray();
        int skipped = 0;

        for (DropTable.Drop drop : def.drops().drops()) {
            Optional<String> item = resolve(drop.item());
            if (item.isEmpty()) {
                LOGGER.debug("Block {} drops \"{}\", which is not registered; skipping that drop",
                    def.qualifiedName(), drop.item());
                skipped++;
                continue;
            }
            addDropPools(pools, blockId, item.get(), drop, maxAge);
        }

        if (pools.isEmpty()) {
            return new LootResult(false, skipped);
        }

        JsonArray functions = new JsonArray();
        functions.add(namedFunction("minecraft:explosion_decay"));

        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:block");
        root.addProperty("random_sequence", XiesMod.MOD_ID + ":blocks/" + path);
        root.add("functions", functions);
        root.add("pools", pools);

        PackJson.write(dataRoot.resolve("loot_table").resolve("blocks").resolve(path + ".json"), root);
        return new LootResult(true, skipped);
    }

    /**
     * Turns one {@code drops} entry into pools.
     *
     * <p>Legacy semantics, from {@code Droppables.getDropsForMetaAndMultiplier}:
     * {@code amount} is a number of <em>independent</em> rolls, each yielding a
     * single item at {@code chance} percent — not a stack size. That maps exactly
     * onto a pool with {@code rolls} set to the amount and the chance as a
     * per-entry condition, because pool conditions are tested once but entry
     * conditions are tested on every roll.
     *
     * <p>The chance map is per age, so contiguous ages sharing a chance are
     * collapsed into one pool with a ranged {@code age} predicate. Ages that can
     * never drop the item produce no pool at all.
     */
    private void addDropPools(JsonArray pools, String blockId, String itemId,
                              DropTable.Drop drop, int maxAge) {
        if (drop.isUnconditional()) {
            pools.add(dropPool(drop.amount(), null, itemId, 100));
            return;
        }

        int age = 0;
        while (age <= maxAge) {
            int chance = clampPercent(drop.chanceFor(age));
            int last = age;
            while (last < maxAge && clampPercent(drop.chanceFor(last + 1)) == chance) {
                last++;
            }
            if (chance > 0) {
                pools.add(dropPool(drop.amount(), ageCondition(blockId, age, last, maxAge), itemId, chance));
            }
            age = last + 1;
        }
    }

    private JsonObject dropPool(int rolls, JsonObject ageCondition, String itemId, int chance) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", itemId);
        if (chance < 100) {
            JsonObject randomChance = new JsonObject();
            randomChance.addProperty("condition", "minecraft:random_chance");
            randomChance.addProperty("chance", chance / 100.0f);
            entry.add("conditions", single(randomChance));
        }

        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", rolls);
        if (ageCondition != null) {
            pool.add("conditions", single(ageCondition));
        }
        pool.add("entries", single(entry));
        return pool;
    }

    /**
     * A {@code block_state_property} predicate over an age range, or null when the
     * range covers every age and the condition would be dead weight.
     */
    private JsonObject ageCondition(String blockId, int from, int to, int maxAge) {
        if (from <= 0 && to >= maxAge) {
            return null;
        }
        JsonObject properties = new JsonObject();
        if (from == to) {
            properties.addProperty("age", String.valueOf(from));
        } else {
            // The ranged form takes strings and parses them through the property,
            // so an IntegerProperty compares numerically rather than lexically.
            JsonObject range = new JsonObject();
            range.addProperty("min", String.valueOf(from));
            range.addProperty("max", String.valueOf(to));
            properties.add("age", range);
        }

        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:block_state_property");
        condition.addProperty("block", blockId);
        condition.add("properties", properties);
        return condition;
    }

    private static int clampPercent(int percent) {
        return Math.clamp(percent, 0, 100);
    }

    // ------------------------------------------------------------ alias tags

    /**
     * Writes one item tag per alias, merged across packs so two packs contributing
     * to {@code $protein} share a tag rather than overwriting each other.
     *
     * <p>Members that are not registered are left out rather than emitted as
     * optional entries: an alias over six items where four are unported should
     * still craft with the two that exist.
     */
    private int writeAliasTags(List<ContentPack> packs) throws IOException {
        Map<String, Set<String>> members = new TreeMap<>();

        for (ContentPack pack : packs) {
            for (AliasDef alias : pack.aliases().values()) {
                Set<String> ids = members.computeIfAbsent(alias.name(), name -> new LinkedHashSet<>());
                for (ItemRef member : alias.members()) {
                    resolve(member).ifPresentOrElse(ids::add, () ->
                        LOGGER.debug("Alias ${} names \"{}\", which is not registered; leaving it out",
                            alias.name(), member));
                }
            }
        }

        int written = 0;
        for (Map.Entry<String, Set<String>> entry : members.entrySet()) {
            String path = ResourceNames.toPath(entry.getKey());
            if (entry.getValue().isEmpty()) {
                LOGGER.debug("Alias ${} has no registered members; recipes using it will be skipped",
                    entry.getKey());
                continue;
            }
            JsonArray values = new JsonArray();
            entry.getValue().forEach(values::add);

            JsonObject root = new JsonObject();
            root.add("values", values);
            PackJson.write(dataRoot.resolve("tags").resolve("item").resolve(path + ".json"), root);

            aliasTags.put(entry.getKey(), "#" + XiesMod.MOD_ID + ":" + path);
            written++;
        }
        return written;
    }

    // --------------------------------------------------------------- recipes

    /** @return how many recipe files this definition produced; 0 means it was skipped */
    private int writeRecipe(RecipeDef recipe) throws IOException {
        return switch (recipe.kind()) {
            case SHAPED -> writeShaped(recipe) ? 1 : 0;
            case SHAPELESS -> writeShapeless(recipe) ? 1 : 0;
            case SMELTING -> writeSmelting(recipe);
            case CULL -> 0;
        };
    }

    private boolean writeShaped(RecipeDef recipe) throws IOException {
        Optional<JsonObject> result = result(recipe);
        if (result.isEmpty()) {
            return false;
        }

        List<String> rows = recipe.pattern();
        int width = rows.stream().mapToInt(String::length).max().orElse(0);
        if (rows.isEmpty() || rows.size() > MAX_GRID || width > MAX_GRID) {
            LOGGER.debug("Skipping recipe {}: pattern is {}x{}, larger than the {}x{} grid",
                recipe.origin(), width, rows.size(), MAX_GRID, MAX_GRID);
            return false;
        }

        Map<Character, ItemRef> symbols = recipe.symbolMap();
        JsonObject key = new JsonObject();
        for (Map.Entry<Character, ItemRef> bound : symbols.entrySet()) {
            Optional<String> ingredient = ingredient(bound.getValue());
            if (ingredient.isEmpty()) {
                LOGGER.debug("Skipping recipe {}: ingredient \"{}\" is not available",
                    recipe.origin(), bound.getValue());
                return false;
            }
            key.addProperty(String.valueOf(bound.getKey()), ingredient.get());
        }

        JsonArray pattern = new JsonArray();
        for (String row : rows) {
            String padded = row + " ".repeat(width - row.length());
            for (int i = 0; i < padded.length(); i++) {
                char symbol = padded.charAt(i);
                if (symbol != ' ' && !symbols.containsKey(symbol)) {
                    // symbolMap binds positionally and gives up when it runs out of
                    // ingredients; the parser already warned, so just drop it.
                    LOGGER.debug("Skipping recipe {}: pattern symbol '{}' has no ingredient",
                        recipe.origin(), symbol);
                    return false;
                }
            }
            pattern.add(padded);
        }

        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:crafting_shaped");
        root.add("key", key);
        root.add("pattern", pattern);
        root.add("result", result.get());
        return write(recipe, "", root);
    }

    private boolean writeShapeless(RecipeDef recipe) throws IOException {
        Optional<JsonObject> result = result(recipe);
        if (result.isEmpty()) {
            return false;
        }
        if (recipe.ingredients().isEmpty() || recipe.ingredients().size() > MAX_GRID * MAX_GRID) {
            LOGGER.debug("Skipping recipe {}: {} ingredients does not fit the grid",
                recipe.origin(), recipe.ingredients().size());
            return false;
        }

        // Ingredient stack counts are ignored: one reference is one grid slot, which
        // is what the 1.6 shapeless matcher did too. Content that wants two of
        // something writes it twice.
        JsonArray ingredients = new JsonArray();
        for (ItemRef ref : recipe.ingredients()) {
            Optional<String> ingredient = ingredient(ref);
            if (ingredient.isEmpty()) {
                LOGGER.debug("Skipping recipe {}: ingredient \"{}\" is not available",
                    recipe.origin(), ref);
                return false;
            }
            ingredients.add(ingredient.get());
        }

        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:crafting_shapeless");
        root.add("ingredients", ingredients);
        root.add("result", result.get());
        return write(recipe, "", root);
    }

    /**
     * {@code "smelt"} may name several inputs, which the original turned into one
     * furnace recipe each. There is no "any of these smelts to that" recipe type,
     * so that is still one file per input.
     */
    private int writeSmelting(RecipeDef recipe) throws IOException {
        Optional<JsonObject> result = result(recipe);
        if (result.isEmpty()) {
            return 0;
        }

        int written = 0;
        List<ItemRef> inputs = recipe.ingredients();
        for (int i = 0; i < inputs.size(); i++) {
            Optional<String> ingredient = ingredient(inputs.get(i));
            if (ingredient.isEmpty()) {
                LOGGER.debug("Skipping smelting of \"{}\" in {}: it is not available",
                    inputs.get(i), recipe.origin());
                continue;
            }
            JsonObject root = new JsonObject();
            root.addProperty("type", "minecraft:smelting");
            root.addProperty("ingredient", ingredient.get());
            root.addProperty("experience", SMELT_EXPERIENCE);
            root.addProperty("cookingtime", SMELT_TIME);
            // Deep-copy the result: several files may be written from one definition.
            root.add("result", result.get().deepCopy());

            if (write(recipe, inputs.size() > 1 ? "_from_" + i : "", root)) {
                written++;
            }
        }
        return written;
    }

    /** The {@code result} object, or empty when the product is not registered. */
    private Optional<JsonObject> result(RecipeDef recipe) {
        Optional<ItemRef> product = recipe.product();
        if (product.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> id = resolve(product.get());
        if (id.isEmpty()) {
            LOGGER.debug("Skipping recipe {}: product \"{}\" is not registered",
                recipe.origin(), product.get());
            return Optional.empty();
        }
        JsonObject result = new JsonObject();
        result.addProperty("id", id.get());
        if (product.get().count() > 1) {
            result.addProperty("count", product.get().count());
        }
        return Optional.of(result);
    }

    private boolean write(RecipeDef recipe, String suffix, JsonObject root) throws IOException {
        String id = uniqueRecipeId(ResourceNames.toPath(recipe.name()) + suffix);
        PackJson.write(dataRoot.resolve("recipe").resolve(id + ".json"), root);
        return true;
    }

    /**
     * Recipe names are unique within a file, not within a pack — two files may both
     * declare {@code tomatoSeeds}. The first one keeps the plain id.
     */
    private String uniqueRecipeId(String base) {
        if (usedRecipeIds.add(base)) {
            return base;
        }
        for (int n = 2; ; n++) {
            String candidate = base + "_" + n;
            if (usedRecipeIds.add(candidate)) {
                return candidate;
            }
        }
    }

    // ------------------------------------------------------------ resolution

    /** An ingredient reference: a concrete item, or an alias standing for a tag. */
    private Optional<String> ingredient(ItemRef ref) {
        return switch (ref.kind()) {
            case ITEM -> resolve(ref);
            case ALIAS -> Optional.ofNullable(aliasTags.get(ref.name()));
            // "#food" and friends were supplied by the engine itself; there is
            // nothing to map them onto until those groups are ported.
            case BUILTIN_GROUP -> Optional.empty();
        };
    }

    /**
     * Resolves one reference to a namespaced item id, or empty when nothing by that
     * name exists.
     *
     * <p>A name a pack declares resolves into this mod. When that name is not in
     * the registry it must <em>not</em> quietly become the vanilla object of the
     * same name: a pack declaring its own {@code campfire} that this build cannot
     * register yet would otherwise have its recipe silently rewritten into one that
     * crafts Mojang's campfire out of four sticks. An unported definition is
     * unresolvable, and the recipe waits for the archetype.
     *
     * <p>A definition that declares {@code override} is the exception — replacing a
     * vanilla object is precisely what it asked for, so until overrides are ported
     * the vanilla object is the right answer.
     */
    private Optional<String> resolve(ItemRef ref) {
        if (ref.isGroup()) {
            return Optional.empty();
        }
        if (declaredNames.contains(ref.name())) {
            String id = XiesMod.MOD_ID + ":" + ResourceNames.toPath(ref.name());
            if (lookup.exists(id)) {
                return Optional.of(id);
            }
            if (!overriddenNames.contains(ref.name())) {
                return Optional.empty();
            }
        }
        String vanilla = "minecraft:" + ResourceNames.toVanillaPath(ref);
        return lookup.exists(vanilla) ? Optional.of(vanilla) : Optional.empty();
    }

    // ---------------------------------------------------------------- plumbing

    private static JsonObject namedFunction(String function) {
        JsonObject json = new JsonObject();
        json.addProperty("function", function);
        return json;
    }

    private static JsonArray single(JsonObject element) {
        JsonArray array = new JsonArray();
        array.add(element);
        return array;
    }
}
