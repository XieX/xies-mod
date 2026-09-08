package mod.xie.xm.cook;

import mod.xie.xm.cook.StovePot.Base;
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
 * The recipes in the original mod posting are the specification here, so the first
 * test walks them literally. Everything after it covers behaviour the posting does
 * not describe, which is the batching the port adds.
 *
 * <p>The posting listed eight recipes; four are tested. The other four were the oil
 * base, cut because it shared this one's ingredients, flavourings and food values
 * and so was three more items for no behaviour. See {@link StovePot}.
 */
class StovePotTest {

    private static StovePot pot() {
        return new StovePot(8, 4);
    }

    /** Add a base, then n foods, then m flavourings, then take one meal out. */
    private static Optional<Meal> cook(Base base, int foods, int flavourings) {
        StovePot pot = pot();
        assertTrue(pot.setBase(base), "base should be accepted by an empty pot");
        for (int i = 0; i < foods; i++) {
            assertTrue(pot.addFood(), "food " + (i + 1) + " should be accepted");
        }
        for (int i = 0; i < flavourings; i++) {
            assertTrue(pot.addFlavour(), "flavouring " + (i + 1) + " should be accepted");
        }
        return pot.extract();
    }

    // -- the posted recipes ---------------------------------------------------

    @Test
    void reproducesThePostedRecipes() {
        assertEquals(Optional.of(Meal.WEAK_SOUP), cook(Base.WATER, 1, 0));
        assertEquals(Optional.of(Meal.SOUP), cook(Base.WATER, 1, 1));
        assertEquals(Optional.of(Meal.SOUP), cook(Base.WATER, 2, 0));
        assertEquals(Optional.of(Meal.STEW), cook(Base.WATER, 2, 1));
    }

    @Test
    void tierIsFoodPlusFlavouring() {
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

    // -- batching -------------------------------------------------------------

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
    void sixFoodAndThreeFlavouringsMakeThreeStews() {
        StovePot pot = pot();
        pot.setBase(Base.WATER);
        for (int i = 0; i < 6; i++) {
            pot.addFood();
        }
        for (int i = 0; i < 3; i++) {
            pot.addFlavour();
        }

        assertEquals(List.of(Meal.STEW, Meal.STEW, Meal.STEW), drain(pot));
        assertTrue(pot.isEmpty(), "draining the pot should leave it empty");
    }

    @Test
    void takesTheHighestTierFirstAndThenWhatIsLeft() {
        StovePot pot = pot();
        pot.setBase(Base.WATER);
        for (int i = 0; i < 3; i++) {
            pot.addFood();
        }
        pot.addFlavour();

        // 3 food + 1 flavouring: a stew takes 2+1, leaving one food for a weak soup.
        assertEquals(List.of(Meal.STEW, Meal.WEAK_SOUP), drain(pot));
    }

    @Test
    void twoFoodsAreSpentBeforeAFlavouringAtTheSameTier() {
        StovePot pot = pot();
        pot.setBase(Base.WATER);
        pot.addFood();
        pot.addFood();
        pot.addFood();
        pot.addFood();
        pot.addFlavour();

        // 4 food + 1 flavouring. The first bowl takes 2+1 for a stew; the second
        // finds 2 food and no flavouring, so it is a soup rather than nothing.
        assertEquals(List.of(Meal.STEW, Meal.SOUP), drain(pot));
    }

    @Test
    void aStrandedFlavouringWaitsForMoreFood() {
        StovePot pot = pot();
        pot.setBase(Base.WATER);
        pot.addFood();
        pot.addFood();
        pot.addFlavour();
        pot.addFlavour();

        assertEquals(Optional.of(Meal.STEW), pot.extract());
        assertEquals(0, pot.food());
        assertEquals(1, pot.flavour(), "the second flavouring should still be in the pot");
        assertFalse(pot.canExtract(), "a flavouring alone is not a meal");

        // The base has not cleared, because the pot is not empty.
        assertEquals(Base.WATER, pot.base());
        assertTrue(pot.addFood());
        assertEquals(Optional.of(Meal.SOUP), pot.extract());
        assertTrue(pot.isEmpty());
    }

    @Test
    void peekDoesNotConsume() {
        StovePot pot = pot();
        pot.setBase(Base.WATER);
        pot.addFood();
        pot.addFood();
        pot.addFlavour();

        assertEquals(Optional.of(Meal.STEW), pot.peek());
        assertEquals(Optional.of(Meal.STEW), pot.peek());
        assertEquals(2, pot.food());
        assertEquals(1, pot.flavour());
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
        StovePot pot = pot();
        pot.setBase(Base.WATER);
        pot.addFood();
        pot.addFood();
        pot.addFood();

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
        StovePot pot = new StovePot(2, 1);
        pot.setBase(Base.WATER);

        assertTrue(pot.addFood());
        assertTrue(pot.addFood());
        assertFalse(pot.addFood(), "a full pot should refuse rather than eat the item");
        assertEquals(2, pot.food());

        assertTrue(pot.addFlavour());
        assertFalse(pot.addFlavour());
        assertEquals(1, pot.flavour());
    }

    @Test
    void aPotTooSmallForOneMealIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new StovePot(1, 1));
        assertThrows(IllegalArgumentException.class, () -> new StovePot(2, 0));
    }

    // -- persistence ----------------------------------------------------------

    @Test
    void loadClampsToCapacity() {
        StovePot pot = new StovePot(4, 2);
        pot.load(Base.WATER, 99, 99);

        assertEquals(4, pot.food());
        assertEquals(2, pot.flavour());
    }

    @Test
    void loadingNoBaseDiscardsAnyContents() {
        StovePot pot = pot();
        pot.load(Base.NONE, 5, 5);

        assertTrue(pot.isEmpty(), "ingredients cannot outlive the liquid they sat in");
    }

    @Test
    void clearEmptiesEverything() {
        StovePot pot = pot();
        pot.setBase(Base.WATER);
        pot.addFood();
        pot.addFlavour();
        pot.clear();

        assertTrue(pot.isEmpty());
        assertEquals(Base.NONE, pot.base());
    }
}
