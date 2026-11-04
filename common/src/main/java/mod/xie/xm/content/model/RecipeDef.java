package mod.xie.xm.content.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One entry from a {@code "recipes"} section.
 *
 * <p>Source shapes, all normalised to this record:
 *
 * <pre>
 *   "tomatoSeeds": "tomato"                              shapeless, key is the product
 *   "campfire": { "pattern": "XX,XX", "content": "stick" }   shaped
 *   "sammichS": { "product": "snackSammich:2",
 *                 "content": "bread,bread,$salady" }     named, aliased, shapeless
 *   "potash":  { "smelt": "..." }                        smelting
 *   "cull": "tile.melon"                                 removes vanilla recipes
 * </pre>
 *
 * <p>Ingredients keep their declaration order because shaped recipes bind pattern
 * symbols to them positionally — see {@link #symbolMap()}.
 */
public record RecipeDef(
    Origin origin,
    String name,
    Kind kind,
    Optional<ItemRef> product,
    List<String> pattern,
    List<ItemRef> ingredients,
    List<ItemRef> culls,
    PropertyMap props
) {

    public enum Kind { SHAPED, SHAPELESS, SMELTING, CULL }

    /** Vanilla's crafting grid is 3x3; the original rejected anything larger. */
    public static final int MAX_GRID = 3;

    public RecipeDef {
        pattern = List.copyOf(pattern);
        ingredients = List.copyOf(ingredients);
        culls = List.copyOf(culls);
    }

    /**
     * Binds pattern symbols to ingredients.
     *
     * <p>Reproduces {@code Recipe.parsePattern}: distinct non-space characters in
     * order of first appearance, bound positionally to the ingredient list. So
     * {@code "XY,YX"} with {@code "a,b"} gives {@code X=a, Y=b}.
     *
     * <p>Returns an empty map for anything that is not shaped.
     */
    public Map<Character, ItemRef> symbolMap() {
        if (kind != Kind.SHAPED) {
            return Map.of();
        }
        Map<Character, ItemRef> bound = new LinkedHashMap<>();
        int next = 0;
        for (String row : pattern) {
            for (int i = 0; i < row.length(); i++) {
                char symbol = row.charAt(i);
                if (symbol == ' ' || bound.containsKey(symbol)) {
                    continue;
                }
                if (next >= ingredients.size()) {
                    // More symbols than ingredients: caller reports it via problems().
                    return bound;
                }
                bound.put(symbol, ingredients.get(next++));
            }
        }
        return bound;
    }

    /** True when any ingredient is an alias, meaning this expands to several recipes. */
    public boolean usesAliases() {
        return ingredients.stream().anyMatch(ItemRef::isGroup);
    }

    /**
     * Structural problems detectable without resolving any reference. Returns an
     * empty list for a well-formed recipe.
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (kind == Kind.CULL) {
            if (culls.isEmpty()) {
                problems.add("cull recipe names nothing to remove");
            }
            return problems;
        }
        if (product.isEmpty()) {
            problems.add("no product");
        }
        if (ingredients.isEmpty()) {
            problems.add("no ingredients");
        }
        if (kind == Kind.SHAPED) {
            if (pattern.size() > MAX_GRID) {
                problems.add("pattern has " + pattern.size() + " rows, max is " + MAX_GRID);
            }
            for (String row : pattern) {
                if (row.length() > MAX_GRID) {
                    problems.add("pattern row \"" + row + "\" is longer than " + MAX_GRID);
                }
            }
            int distinct = (int) pattern.stream()
                .flatMapToInt(String::chars)
                .filter(c -> c != ' ')
                .distinct()
                .count();
            if (distinct > ingredients.size()) {
                problems.add("pattern uses " + distinct + " symbols but only "
                    + ingredients.size() + " ingredients were given");
            }
        }
        if (kind == Kind.SHAPELESS && ingredients.size() > MAX_GRID * MAX_GRID) {
            problems.add("shapeless recipe has " + ingredients.size()
                + " ingredients, max is " + (MAX_GRID * MAX_GRID));
        }
        return problems;
    }
}
