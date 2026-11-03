package mod.xie.xm.cook;

import java.util.Optional;

/**
 * The stove's cooking pot: what is in it, and what can be ladled out of it.
 *
 * <p>This is the one part of the port with no 2013 source to read. The Cooking Mod
 * pack that held the stove was lost, so the rules below are reconstructed from the
 * original mod posting, which listed eight recipes:
 *
 * <pre>
 *   Water... food... bowl                        =&gt; Weak Soup
 *   Water... food... flavouring... bowl          =&gt; Soup
 *   Water... food... food... bowl                =&gt; Soup
 *   Water... food... food... flavouring... bowl  =&gt; Stew
 * </pre>
 *
 * <p>Those collapse to one rule, tier = food + flavouring, which is what the port
 * shipped first. It was replaced in November 2026 by a table with a third class of
 * ingredient, <b>protein</b>, so that meat has a job in the pot:
 *
 * <pre>
 *   food                        =&gt; Weak Soup   (vegetable laundering)
 *   food + flavouring           =&gt; Soup       ("upgraded" vegetables)
 *   protein                     =&gt; Soup       (protein laundering)
 *   protein + food + flavouring =&gt; Stew
 * </pre>
 *
 * <p>A Stew is the ingredients of two Soups in one bowl, so it is never the most
 * food per ingredient; it is the most food per bowl, the same trade vanilla's rabbit
 * stew makes. Nothing here depends on <em>which</em> food went in, only on its class,
 * which is still the posting's design: condensing assorted low-value ingredients
 * into a few homogeneous medium-value meals.
 *
 * <p>The posting listed the same four again with Oil in place of Water, yielding
 * Fried Mess, Fried Meal and Stirfry. That half is <b>deliberately not ported</b>: it
 * shared this one's ingredient list, its flavouring list and its food values, so it
 * was six items doing the work of three and six sprites to draw for one behaviour.
 * {@link Base} keeps room for a second base rather than collapsing to a boolean, so
 * reinstating it means adding an enum constant and three meals &mdash; but it should
 * only come back if it comes back <em>different</em>.
 *
 * <p>The other departure from the posting is capacity. The original pot cooked one
 * meal at a time; this one accumulates up to {@code foodCapacity},
 * {@code flavourCapacity} and {@code proteinCapacity} and lets a bowl be used
 * repeatedly, so the block is a
 * batch cooker rather than a single-recipe station. {@link #extract} therefore takes
 * the <em>highest tier the contents can currently afford</em> and leaves the rest.
 *
 * <p>Deliberately free of any Minecraft type, so the rules can be tested directly.
 */
public final class StovePot {

    /**
     * What the pot was started with.
     *
     * <p>Two values where the posting had three, the oil base having been cut. Kept
     * as an enum rather than reduced to "has liquid" because the base is the thing a
     * second family of meals would hang off, and because {@link #mealFor} reads
     * better as a table than as a branch.
     */
    public enum Base {
        /** Nothing in the pot; the next base item sets it. */
        NONE,
        WATER
    }

    /** The three things a stove can produce. Ids match the {@code cooking/cookpot.json} item keys. */
    public enum Meal {
        WEAK_SOUP("weak_soup"),
        SOUP("soup"),
        STEW("stew");

        private final String path;

        Meal(String path) {
            this.path = path;
        }

        /** The registered id path, e.g. {@code weak_soup}. */
        public String path() {
            return path;
        }
    }

    /** The three classes of thing that go into a started pot. */
    public enum Ingredient {
        FOOD,
        FLAVOUR,
        PROTEIN
    }

    private final int foodCapacity;
    private final int flavourCapacity;
    private final int proteinCapacity;

    private Base base = Base.NONE;
    private int food;
    private int flavour;
    private int protein;

    /**
     * @throws IllegalArgumentException if any capacity is below one, since a Stew
     *         takes one of each and a pot that could never make one is a content error
     */
    public StovePot(int foodCapacity, int flavourCapacity, int proteinCapacity) {
        if (foodCapacity < 1 || flavourCapacity < 1 || proteinCapacity < 1) {
            throw new IllegalArgumentException(
                "a pot too small for a stew can never make one: " + foodCapacity + " food, "
                    + flavourCapacity + " flavouring, " + proteinCapacity + " protein");
        }
        this.foodCapacity = foodCapacity;
        this.flavourCapacity = flavourCapacity;
        this.proteinCapacity = proteinCapacity;
    }

    public Base base() {
        return base;
    }

    public int food() {
        return food;
    }

    public int flavour() {
        return flavour;
    }

    public int protein() {
        return protein;
    }

    public int foodCapacity() {
        return foodCapacity;
    }

    public int flavourCapacity() {
        return flavourCapacity;
    }

    public int proteinCapacity() {
        return proteinCapacity;
    }

    /** Whether the pot holds nothing at all &mdash; no base and no ingredients. */
    public boolean isEmpty() {
        return base == Base.NONE && food == 0 && flavour == 0 && protein == 0;
    }

    /**
     * Start the pot with water or oil.
     *
     * <p>Only an empty pot accepts a base, so a half-finished stew cannot be turned
     * into a stirfry partway through. The base persists across extractions and clears
     * only when the pot runs dry, which is what lets one bucket of water serve a whole
     * batch.
     *
     * @return whether the base was accepted
     */
    public boolean setBase(Base newBase) {
        if (newBase == null || newBase == Base.NONE || !isEmpty()) {
            return false;
        }
        base = newBase;
        return true;
    }

    /**
     * Add one food ingredient.
     *
     * @return whether it was accepted; false if the pot has no base or is full
     */
    public boolean addFood() {
        if (base == Base.NONE || food >= foodCapacity) {
            return false;
        }
        food++;
        return true;
    }

    /**
     * Add one flavouring.
     *
     * @return whether it was accepted; false if the pot has no base or is full
     */
    public boolean addFlavour() {
        if (base == Base.NONE || flavour >= flavourCapacity) {
            return false;
        }
        flavour++;
        return true;
    }

    /**
     * Add one protein.
     *
     * @return whether it was accepted; false if the pot has no base or is full
     */
    public boolean addProtein() {
        if (base == Base.NONE || protein >= proteinCapacity) {
            return false;
        }
        protein++;
        return true;
    }

    /** Add one ingredient of the given class. */
    public boolean add(Ingredient ingredient) {
        return switch (ingredient) {
            case FOOD -> addFood();
            case FLAVOUR -> addFlavour();
            case PROTEIN -> addProtein();
        };
    }

    /**
     * Whether a bowl used now would produce anything. Equivalent to {@link #peek}
     * being present, and cheaper to read at a glance.
     */
    public boolean canExtract() {
        return plan().isPresent();
    }

    /** What the next bowl would yield, without consuming anything. */
    public Optional<Meal> peek() {
        return plan().map(claim -> mealFor(base, claim.tier()));
    }

    /**
     * Ladle out the best meal the pot can currently afford, consuming its ingredients.
     *
     * <p>The base is left alone unless this empties the pot, in which case it clears
     * and the stove is ready to be started again with water or oil.
     *
     * @return the meal produced, or empty if there is not enough in the pot
     */
    public Optional<Meal> extract() {
        Optional<Claim> claim = plan();
        if (claim.isEmpty()) {
            return Optional.empty();
        }

        Claim taken = claim.get();
        Meal meal = mealFor(base, taken.tier());

        food -= taken.food();
        flavour -= taken.flavour();
        protein -= taken.protein();
        if (food == 0 && flavour == 0 && protein == 0) {
            base = Base.NONE;
        }
        return Optional.of(meal);
    }

    /** Empty the pot entirely, discarding whatever is in it. */
    public void clear() {
        base = Base.NONE;
        food = 0;
        flavour = 0;
        protein = 0;
    }

    /** Restore saved contents. Values outside the pot's capacity are clamped. */
    public void load(Base savedBase, int savedFood, int savedFlavour, int savedProtein) {
        base = savedBase == null ? Base.NONE : savedBase;
        if (base == Base.NONE) {
            food = 0;
            flavour = 0;
            protein = 0;
            return;
        }
        food = Math.clamp(savedFood, 0, foodCapacity);
        flavour = Math.clamp(savedFlavour, 0, flavourCapacity);
        protein = Math.clamp(savedProtein, 0, proteinCapacity);
    }

    /** What one extraction would take out of the pot, and the tier it makes. */
    private record Claim(int food, int flavour, int protein, int tier) {
    }

    /**
     * Choose what the next bowl takes: the highest tier the contents can afford.
     *
     * <p>The two ways to a Soup never compete. Protein alone and food with
     * flavouring are both available only when all three classes are, and then a
     * Stew is taken instead.
     *
     * <p>A flavouring only upgrades food. Protein with flavouring and no food is a
     * protein Soup, and the flavouring stays in the pot for the next vegetable; it
     * is not lost, and a pot holding nothing but flavouring shows raw, which says
     * there is something still to add.
     */
    private Optional<Claim> plan() {
        if (base == Base.NONE) {
            return Optional.empty();
        }
        if (protein >= 1 && food >= 1 && flavour >= 1) {
            return Optional.of(new Claim(1, 1, 1, 3));
        }
        if (protein >= 1) {
            return Optional.of(new Claim(0, 0, 1, 2));
        }
        if (food >= 1 && flavour >= 1) {
            return Optional.of(new Claim(1, 1, 0, 2));
        }
        if (food >= 1) {
            return Optional.of(new Claim(1, 0, 0, 1));
        }
        return Optional.empty();
    }

    /** The meal a base and a tier name: 1, 2 or 3 for every claim the pot makes. */
    public static Meal mealFor(Base base, int tier) {
        return switch (base) {
            case WATER -> switch (tier) {
                case 1 -> Meal.WEAK_SOUP;
                case 2 -> Meal.SOUP;
                case 3 -> Meal.STEW;
                default -> throw new IllegalArgumentException("no water meal at tier " + tier);
            };
            case NONE -> throw new IllegalArgumentException("an empty pot makes nothing");
        };
    }
}
