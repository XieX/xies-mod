package mod.xie.xm.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;

/**
 * {@code cull} — the one recipe kind that removes rather than adds.
 *
 * <p>A cull names the <em>output</em> of a recipe and deletes every recipe that
 * produces it. {@code JobRecipe.doCulling} walked {@code CraftingManager}'s list and
 * dropped each entry whose {@code getRecipeOutput()} matched. Recipes are a datapack
 * registry now, loaded with no hook between reading a file and registering it, and a
 * datapack overrides a recipe by <em>id</em> while a cull knows none.
 *
 * <p>So the ids are found here, at generation, by reading the result of every recipe
 * in vanilla's own data, and each recipe a cull names is overridden by a file that
 * disables itself with {@code neoforge:never}, as is each vanilla advancement naming
 * only those recipes. That covers the recipes shipped in the game jar, which is
 * everything a cull has been written against. A recipe another mod or a world's
 * datapack adds is not seen.
 *
 * <p>Reading the result out of raw JSON is not as fragile as it looks: every recipe
 * type in the game, including the special ones, writes {@code "result": {"id": ...}},
 * because that field is an {@code ItemStack} and its codec has one shape. The ones
 * that have no result at all — the smithing trims and repairing two items together —
 * produce nothing that could be culled anyway.
 *
 * <p>This mod's own recipes are never culled, because they are not vanilla's. The
 * content that culls a thing is invariably the content that replaces it:
 * {@code cullStoneTools.json} culls the stone tools, and its pack declares flint ones.
 */
public final class XieRecipeCuller {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String RECIPE_DIR = "recipe";
    private static final String ADVANCEMENT_DIR = "advancement";

    private XieRecipeCuller() { }

    /**
     * Every vanilla recipe file whose result is one of {@code culled}, as
     * {@code minecraft:recipe/red_dye_from_poppy.json}, sorted.
     *
     * @param culled namespaced item ids, resolved from the content's {@code cull} entries
     */
    public static Set<Identifier> vanillaRecipesProducing(Set<String> culled) {
        // Sorted, because the log line is the only summary of what a cull did and an
        // unstable order makes two runs look different when they are not.
        Set<Identifier> found = new TreeSet<>();
        if (!culled.isEmpty()) {
            scanVanilla(RECIPE_DIR, (file, recipe) -> {
                String result = resultId(recipe);
                if (result != null && culled.contains(result)) {
                    found.add(file);
                }
            });
        }
        return found;
    }

    /**
     * Every vanilla advancement file that names only recipes in {@code recipeIds}.
     *
     * <p>A recipe id in an advancement is a registry reference, and one naming a recipe
     * that did not load fails the whole datapack. Vanilla's recipe-book advancements
     * name exactly one recipe each, so each goes with its recipe. One that names others
     * as well is kept, with a warning, because removing it would take more than the
     * cull asked for.
     *
     * @param recipeIds namespaced recipe ids, as {@link #recipeId} gives them
     */
    public static Set<Identifier> vanillaAdvancementsNaming(Set<String> recipeIds) {
        Set<Identifier> found = new TreeSet<>();
        if (recipeIds.isEmpty()) {
            return found;
        }
        scanVanilla(ADVANCEMENT_DIR, (file, advancement) -> {
            Set<String> named = new TreeSet<>();
            collectRecipes(advancement, named);
            if (named.stream().noneMatch(recipeIds::contains)) {
                return;
            }
            if (recipeIds.containsAll(named)) {
                found.add(file);
            } else {
                LOGGER.warn("Vanilla advancement {} names a culled recipe among others {}; "
                    + "it will fail to load", file, named);
            }
        });
        return found;
    }

    /** {@code minecraft:recipe/red_dye_from_poppy.json} as the recipe it holds. */
    public static String recipeId(Identifier file) {
        String path = file.getPath();
        path = path.substring(path.indexOf('/') + 1, path.length() - ".json".length());
        return file.getNamespace() + ":" + path;
    }

    private static void scanVanilla(String directory, BiConsumer<Identifier, JsonElement> action) {
        PackResources vanilla = ServerPacksSource.createVanillaPackSource().fullResources();
        vanilla.listResources(PackType.SERVER_DATA, Identifier.DEFAULT_NAMESPACE, directory,
            (file, resource) -> {
                try (Reader reader = new InputStreamReader(resource.get(), StandardCharsets.UTF_8)) {
                    action.accept(file, JsonParser.parseReader(reader));
                } catch (IOException | RuntimeException e) {
                    LOGGER.warn("Could not read vanilla {} to check it against the culls", file, e);
                }
            });
    }

    /** Every value under a {@code recipes} key, anywhere: rewards and criteria both use it. */
    private static void collectRecipes(JsonElement json, Set<String> into) {
        if (json instanceof JsonObject object) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getKey().equals("recipes")) {
                    addStrings(entry.getValue(), into);
                } else {
                    collectRecipes(entry.getValue(), into);
                }
            }
        } else if (json instanceof JsonArray array) {
            array.forEach(element -> collectRecipes(element, into));
        }
    }

    private static void addStrings(JsonElement json, Set<String> into) {
        if (json instanceof JsonArray array) {
            array.forEach(element -> addStrings(element, into));
        } else if (json.isJsonPrimitive()) {
            into.add(json.getAsString());
        }
    }

    /** A recipe file that loads as nothing, which is how an override removes one. */
    public static JsonObject disabled() {
        JsonObject never = new JsonObject();
        never.addProperty("type", "neoforge:never");
        JsonArray conditions = new JsonArray();
        conditions.add(never);
        JsonObject root = new JsonObject();
        root.add("neoforge:conditions", conditions);
        return root;
    }

    /** The item a recipe produces, or null when it has none this can read. */
    private static String resultId(JsonElement recipe) {
        if (!(recipe instanceof JsonObject object)) {
            return null;
        }
        JsonElement result = object.get("result");
        if (!(result instanceof JsonObject stack)) {
            return null;
        }
        JsonElement id = stack.get("id");
        return id != null && id.isJsonPrimitive() ? id.getAsString() : null;
    }
}
