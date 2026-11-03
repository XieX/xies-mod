package mod.xie.xm.cook;

import mod.xie.xm.cook.StovePot.Base;
import mod.xie.xm.cook.StovePot.Ingredient;
import mod.xie.xm.cook.StovePot.Meal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The recipe table in {@link StovePot} is the specification here, so the first test
 * walks it literally. Everything after it covers what the table does not say: the
 * combinations it leaves out, and the batching the port adds.
 */
class StovePotTest {

    private static StovePot pot() {
        return new StovePot(8, 4, 4);
    }

    /** Add a base, then the given ingredients, then take one meal out. */
    private static Optional<Meal> cook(int foods, int flavourings, int proteins) {
        StovePot pot = filled(foods, flavourings, proteins);
        return pot.extract();
    }

    private static StovePot filled(int foods, int flavourings, int proteins) {
        StovePot pot = pot();
        assertTrue(pot.setBase(Base.WATER), "base should be accepted by an empty pot");
        for (int i = 0; i < foods; i++) {
            assertTrue(pot.addFood(), "food " + (i + 1) + " should be accepted");
        }
        for (int i = 0; i < flavourings; i++) {
            assertTrue(pot.addFlavour(), "flavouring " + (i + 1) + " should be accepted");
        }
        for (int i = 0; i < proteins; i++) {
            assertTrue(pot.addProtein(), "protein " + (i + 1) + " should be accepted");
        }
        return pot;
    }

    // -- the table ------------------------------------------------------------

    @Test
    void reproducesTheRecipeTable() {
        assertEquals(Optional.of(Meal.WEAK_SOUP), cook(1, 0, 0));
        assertEquals(Optional.of(Meal.SOUP), cook(1, 1, 0));
        assertEquals(Optional.of(Meal.SOUP), cook(0, 0, 1));
        assertEquals(Optional.of(Meal.STEW), cook(1, 1, 1));
    }

    @Test
    void tiersNameTheThreeMeals() {
        assertEquals(Meal.WEAK_SOUP, StovePot.mealFor(Base.WATER, 1));
        assertEquals(Meal.SOUP, StovePot.mealFor(Base.WATER, 2));
        assertEquals(Meal.STEW, StovePot.mealFor(Base.WATER, 3));
    }

    @Test
    void anEmptyPotNamesNoMeal() {
        assertThrows(IllegalArgumentException.class, () -> StovePot.mealFor(Base.NONE, 1));
        assertThrows(IllegalArgumentException.class, () -> StovePot.mealFor(Base.WATER, 4));
        assertThrows(IllegalArgumentException.class, () -> StovePot.mealFor(Base.WATER, 0));
    }

    // -- what the table leaves out ----------------------------------------------

    /** Drain the pot a bowl at a time until it stops yielding. */
    private static List<Meal> drain(StovePot pot) {
        List<Meal> meals = new ArrayList<>();
        Optional<Meal> next;
        while ((next = pot.extract()).isPresent()) {
            meals.add(next.get());
        }
        return meals;
    }

    @Test
    void twoFoodsAreTwoWeakSoupsNotOneSoup() {
        assertEquals(List.of(Meal.WEAK_SOUP, Meal.WEAK_SOUP), drain(filled(2, 0, 0)));
    }

    @Test
    void proteinAndFoodWithoutFlavouringAreServedApart() {
        assertEquals(List.of(Meal.SOUP, Meal.WEAK_SOUP), drain(filled(1, 0, 1)));
    }

    @Test
    void aFlavouringDoesNotUpgradeProteinAlone() {
        StovePot pot = filled(0, 1, 1);

        assertEquals(Optional.of(Meal.SOUP), pot.extract());
        assertEquals(1, pot.flavour(), "the flavouring should wait for a vegetable");
        assertFalse(pot.canExtract(), "a flavouring alone is not a meal");

        assertTrue(pot.addFood());
        assertEquals(Optional.of(Meal.SOUP), pot.extract());
        assertTrue(pot.isEmpty());
    }

    // -- batching -------------------------------------------------------------

    @Test
    void threeOfEachMakeThreeStews() {
        StovePot pot = filled(3, 3, 3);

        assertEquals(List.of(Meal.STEW, Meal.STEW, Meal.STEW), drain(pot));
        assertTrue(pot.isEmpty(), "draining the pot should leave it empty");
    }

    @Test
    void takesTheHighestTierFirstAndThenWhatIsLeft() {
        // A stew takes one of each; then the second protein is a Soup, and the
        // second food, with no flavouring left, a Weak Soup.
        assertEquals(List.of(Meal.STEW, Meal.SOUP, Meal.WEAK_SOUP), drain(filled(2, 1, 2)));
    }

    @Test
    void peekDoesNotConsume() {
        StovePot pot = filled(1, 1, 1);

        assertEquals(Optional.of(Meal.STEW), pot.peek());
        assertEquals(Optional.of(Meal.STEW), pot.peek());
        assertEquals(1, pot.food());
        assertEquals(1, pot.flavour());
        assertEquals(1, pot.protein());
    }

    @Test
    void addDispatchesOnTheIngredientClass() {
        StovePot pot = pot();
        pot.setBase(Base.WATER);

        assertTrue(pot.add(Ingredient.FOOD));
        assertTrue(pot.add(Ingredient.FLAVOUR));
        assertTrue(pot.add(Ingredient.PROTEIN));
        assertEquals(1, pot.food());
        assertEquals(1, pot.flavour());
        assertEquals(1, pot.protein());
    }

    // -- the base -------------------------------------------------------------

    @Test
    void onlyAnEmptyPotAcceptsABase() {
        StovePot pot = pot();
        assertTrue(pot.setBase(Base.WATER));
        assertFalse(pot.setBase(Base.WATER), "a started pot should not take more liquid");

        pot.addFood();
        assertFalse(pot.setBase(Base.WATER), "a pot with food in should not take more liquid");
    }

    @Test
    void theBaseClearsOnlyWhenThePotRunsDry() {
        StovePot pot = filled(1, 0, 1);

        assertEquals(Optional.of(Meal.SOUP), pot.extract());
        assertEquals(Base.WATER, pot.base(), "one food left, so the water stays");

        assertEquals(Optional.of(Meal.WEAK_SOUP), pot.extract());
        assertEquals(Base.NONE, pot.base(), "pot is dry, so the base clears");
        assertTrue(pot.isEmpty());

        // And it can be filled again.
        assertTrue(pot.setBase(Base.WATER));
    }

    @Test
    void nothingIsAcceptedWithoutABase() {
        StovePot pot = pot();
        assertFalse(pot.addFood(), "food needs a liquid to go into");
        assertFalse(pot.addFlavour(), "flavouring needs a liquid to go into");
        assertFalse(pot.addProtein(), "protein needs a liquid to go into");
        assertFalse(pot.canExtract());
        assertEquals(Optional.empty(), pot.extract());
    }

    @Test
    void baseNoneIsNotABase() {
        StovePot pot = pot();
        assertFalse(pot.setBase(Base.NONE));
        assertFalse(pot.setBase(null));
        assertTrue(pot.isEmpty());
    }

    // -- capacity -------------------------------------------------------------

    @Test
    void theCapRejectsRatherThanSwallows() {
        StovePot pot = new StovePot(1, 1, 1);
        pot.setBase(Base.WATER);

        assertTrue(pot.addFood());
        assertFalse(pot.addFood(), "a full pot should refuse rather than eat the item");
        assertTrue(pot.addFlavour());
        assertFalse(pot.addFlavour());
        assertTrue(pot.addProtein());
        assertFalse(pot.addProtein());

        assertEquals(1, pot.food());
        assertEquals(1, pot.flavour());
        assertEquals(1, pot.protein());
    }

    @Test
    void aPotThatCouldNeverMakeAStewIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new StovePot(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new StovePot(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new StovePot(1, 1, 0));
    }

    // -- persistence ----------------------------------------------------------

    @Test
    void loadClampsToCapacity() {
        StovePot pot = new StovePot(4, 2, 3);
        pot.load(Base.WATER, 99, 99, 99);

        assertEquals(4, pot.food());
        assertEquals(2, pot.flavour());
        assertEquals(3, pot.protein());
    }

    @Test
    void loadingNoBaseDiscardsAnyContents() {
        StovePot pot = pot();
        pot.load(Base.NONE, 5, 5, 5);

        assertTrue(pot.isEmpty(), "ingredients cannot outlive the liquid they sat in");
    }

    @Test
    void clearEmptiesEverything() {
        StovePot pot = filled(1, 1, 1);
        pot.clear();

        assertTrue(pot.isEmpty());
        assertEquals(Base.NONE, pot.base());
    }
}
