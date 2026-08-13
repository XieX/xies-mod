package mod.xie.xm.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import mod.xie.xm.XiesMod;

import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;

import org.slf4j.Logger;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code cull} — the one recipe kind that removes rather than adds.
 *
 * <p>A cull names the <em>output</em> of a recipe and deletes every recipe that
 * produces it. {@code JobRecipe.doCulling} walked {@code CraftingManager}'s list and
 * dropped each entry whose {@code getRecipeOutput()} matched; there is no equivalent
 * list to mutate now, and no datapack can express "whatever produces this", because
 * a datapack overrides a recipe by <em>id</em> and a cull does not know any ids.
 *
 * <p>{@link ModifyRecipeJsonsEvent} is the modern shape of that same walk. It fires
 * on the server once the recipe JSONs have been read off every pack and before any
 * of them is deserialised, and hands over the whole map to modify in place — so the
 * question a cull asks can be asked of the raw JSON, of every recipe from every
 * source, and asked again on each {@code /reload}. The alternative, writing a
 * disabled override per victim into the generated pack, needs the victims' ids at
 * emit time and would see only the recipes shipped in the game jar.
 *
 * <p>Reading the result out of raw JSON is what makes this possible, and it is not
 * as fragile as it looks: every recipe type in the game, including the special ones,
 * writes {@code "result": {"id": ...}}, because that field is an {@code ItemStack}
 * and its codec has one shape. The nineteen that have no result at all — the
 * smithing trims and repairing two items together — produce nothing that could be
 * culled anyway.
 *
 * <h2>What is spared</h2>
 *
 * <p>Recipes in this mod's own namespace, always. The content that culls a thing is
 * invariably the content that replaces it: {@code flowerbushes.xie} culls red dye
 * and then declares red dye from rose seeds, and {@code cullStoneTools.xie} culls
 * every stone tool so that {@code flintTools.xie} can craft them from flint instead.
 * The 2013 engine got this from load order — a cull job ran against the recipe list
 * as it stood, and the pack's own recipes were added by later jobs — and the
 * namespace is what preserves it without depending on which file was read first.
 */
public final class XieRecipeCuller {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Item ids whose recipes are to be removed. Written once during pack generation
     * and read on every datapack load, which are different threads.
     */
    private static volatile Set<String> targets = Set.of();

    private XieRecipeCuller() { }

    /** @param itemIds namespaced ids, resolved from the content's {@code cull} entries */
    public static void targets(Set<String> itemIds) {
        targets = Set.copyOf(itemIds);
    }

    public static void onModifyRecipeJsons(ModifyRecipeJsonsEvent event) {
        Set<String> culled = targets;
        if (culled.isEmpty()) {
            return;
        }

        // Sorted, because the log line is the only evidence a cull did anything and
        // an unstable order makes two runs look different when they are not.
        Set<String> removed = new TreeSet<>();
        Iterator<Map.Entry<Identifier, JsonElement>> recipes =
            event.getRecipeJsons().entrySet().iterator();

        while (recipes.hasNext()) {
            Map.Entry<Identifier, JsonElement> recipe = recipes.next();
            if (recipe.getKey().getNamespace().equals(XiesMod.MOD_ID)) {
                continue;
            }
            String result = resultId(recipe.getValue());
            if (result != null && culled.contains(result)) {
                removed.add(recipe.getKey().toString());
                recipes.remove();
            }
        }

        if (removed.isEmpty()) {
            LOGGER.info("Nothing to cull: no recipe produces any of {}", sorted(culled));
            return;
        }
        LOGGER.info("Culled {} recipes producing {}: {}",
            removed.size(), sorted(culled), String.join(", ", removed));
    }

    /**
     * The item a recipe produces, or null when it has none this can read.
     *
     * <p>A recipe wrapped in {@code neoforge:conditions} keeps its own fields at the
     * top level — the conditional encoder copies them up beside the conditions
     * whenever the value it wraps is a map, which a recipe always is — so there is
     * nothing to unwrap here.
     */
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

    private static List<String> sorted(Set<String> ids) {
        return ids.stream().sorted().toList();
    }
}
