"""``cookpot``, the ``stove`` archetype: water, food and a bowl, read back as
a meal.

The rules are unit-tested in ``common/`` (``StovePotTest``) without a world.
What only a running game can say is whether the *interaction* reaches them: a
right-click with a water bucket, a carrot, a lump of coal or an empty bowl,
each resolved by ``XieStoveBlock.useItemOn`` in that order, and the meal
arriving in the player's inventory rather than a name coming out of a method.

Four things are asserted, and every null is taken against a positive control
on the same pot with the same click:

* a water bucket starts an empty pot and comes back as an empty bucket, and a
  second bucket on the same pot is refused with the bucket still full;
* nothing cooks without fuel — a pot with a carrot in it is still raw after
  ``cookTime`` has passed — and once coal goes in it finishes within
  ``cookTime`` plus a margin;
* a bowl on a cooked pot yields the meal the tier predicts: one food is a Weak
  Soup, one food and salt a Soup, two foods and salt a Stew. A bowl on an empty
  pot and on a pot still cooking yields nothing and keeps the bowl;
* an item that is none of base, ingredient, flavouring or fuel is refused with
  the pot and the inventory unchanged.

Two things about the rig. Every click is at a *side* face, from two blocks
away over a daylight detector, rather than from on top: a refused water bucket
falls through to vanilla ``BucketItem``, which empties the water onto the
neighbour of the clicked face — the player's own feet, from above — and a
neighbour it cannot replace is what keeps the bucket full, which is the only
thing that tells a refusal from an acceptance once the pot already holds water.
And the null item is an apple rather than a stick, because a stick is a furnace
fuel and would be stoked rather than refused; the apple is a food the pot does
not list, which is the nearer miss anyway.

Survival throughout: creative would not consume the bucket, the carrot or the
bowl, and consumption is half of what is being read.
"""

import re
import time

from ..rig.arena import Y, lane

NEEDS_CLIENT = True

POT = "xiesmod:cookpot"
WATER = "minecraft:water_bucket"
BUCKET = "minecraft:bucket"
BOWL = "minecraft:bowl"
COAL = "minecraft:coal"
CARROT = "minecraft:carrot"
TOMATO = "xiesmod:tomato"
SALT = "xiesmod:salt"
APPLE = "minecraft:apple"

WEAK_SOUP = "xiesmod:weak_soup"
SOUP = "xiesmod:soup"
STEW = "xiesmod:stew"
MEALS = (WEAK_SOUP, SOUP, STEW)

#: ``cookTime`` from ``cooking/cookpot.json``, in ticks, and how long that is.
COOK_TICKS = 200
COOK_SECONDS = COOK_TICKS / 20.0

#: How much longer than ``cookTime`` a fuelled pot may take before it is a
#: failure: a lagging tick and a polling interval, not a second cook.
MARGIN = 5.0

#: Between the player and the pot when clicking its side. Six sixteenths tall,
#: not waterloggable and not replaceable, so a bucket the pot refuses has
#: nowhere to go.
SPACER = "minecraft:daylight_detector"

#: Aim for a side face from two blocks west: eye height 1.62 down to a point
#: 0.8 up the west face, 1.5 blocks away, which clears the spacer by a block.
AIM_PITCH = 28.7
AIM_YAW = -90.0


def run(game, expect):
    x0, z0 = lane("cookpot")
    game.enter(x0 + 4, z0)
    game.clear((x0 - 3, z0 - 2), (x0 + 11, z0 + 2), floor="minecraft:stone")
    game.kill_items()
    game.gamemode("survival")
    game.heal()
    game.clear_inventory()

    pots = [(x0 + 4 * index, Y, z0) for index in range(3)]
    for pot in pots:
        game.replace(pot, POT)
        game.replace((pot[0] - 1, pot[1], pot[2]), SPACER)

    _weak_soup(game, expect, pots[0])
    _soup(game, expect, pots[1])
    _stew(game, expect, pots[2])

    expect.skip("the fire and steam particles, and the bucket, splash and ladle sounds",
                "no assertion can see or hear them; PORTING.md records the reading")


# ------------------------------------------------------------------ pot one

def _weak_soup(game, expect, pot):
    """Base, fuel and the first tier, with every null the pot has."""
    _aim(game, pot)

    # A bowl on an empty pot: nothing to serve, and the bowl stays.
    game.clear_inventory()
    game.hold(BOWL)
    game.right_click()
    time.sleep(0.6)
    before = _contents(game, pot)
    expect.that("a bowl on an empty pot serves nothing and is kept",
                game.count_items(BOWL) == 1 and _meals(game) == 0
                and before["Base"] == "NONE" and not game.block_is(pot, "%s[lit=true]" % POT),
                "pot %s, %d bowls, %d meals" % (_said(before), game.count_items(BOWL), _meals(game)))

    # Water on an empty pot starts it, and the bucket comes back empty.
    game.clear_inventory()
    game.hold(WATER)
    clicks = game.click_until(lambda: game.block_is(pot, "%s[pot=1]" % POT))
    expect.that("a water bucket on an empty cookpot starts a pot", clicks, _took(clicks))
    expect.that("and leaves the player holding an empty bucket",
                game.count_items(BUCKET) == 1 and game.count_items(WATER) == 0,
                "%d empty, %d full" % (game.count_items(BUCKET), game.count_items(WATER)))
    expect.equal("the pot's base is water", _contents(game, pot)["Base"], "WATER")

    # The null against it: a second bucket on the same pot, same aim, same
    # click. Refused, the bucket is still full; vanilla would have emptied it
    # onto the spacer, which cannot take water.
    game.clear_inventory()
    game.hold(WATER)
    game.right_click()
    time.sleep(0.6)
    after = _contents(game, pot)
    expect.that("a water bucket on a pot that already has a base is refused",
                game.count_items(WATER) == 1 and game.count_items(BUCKET) == 0
                and after["Base"] == "WATER" and after["Food"] == 0,
                "%d full buckets left, pot %s" % (game.count_items(WATER), _said(after)))

    # One food. Accepted whether or not the fire is in; it is the bowl that waits.
    game.clear_inventory()
    game.hold(CARROT)
    clicks = game.click_until(lambda: _contents(game, pot)["Food"] == 1)
    expect.that("a listed ingredient goes into the pot", clicks,
                "%s; %s" % (CARROT, _took(clicks)))
    expect.equal("and is consumed from the hand", game.count_items(CARROT), 0)

    # No fuel, no cooking: after a whole cookTime the countdown has not moved.
    time.sleep(COOK_SECONDS + 2.0)
    unfuelled = _contents(game, pot)
    expect.that("with no fuel the pot never finishes cooking",
                unfuelled["CookProgress"] == COOK_TICKS and game.block_is(pot, "%s[pot=1,lit=false]" % POT),
                "after %.0fs the countdown reads %s of %d"
                % (COOK_SECONDS + 2.0, unfuelled["CookProgress"], COOK_TICKS))

    # A bowl on a pot that is not done: nothing served, and the bowl kept.
    game.clear_inventory()
    game.hold(BOWL)
    game.right_click()
    time.sleep(0.6)
    raw = _contents(game, pot)
    expect.that("a bowl on a pot still cooking serves nothing and is kept",
                game.count_items(BOWL) == 1 and _meals(game) == 0
                and raw["Food"] == 1 and raw["BurnTime"] == 0,
                "pot %s, %d bowls, %d meals" % (_said(raw), game.count_items(BOWL), _meals(game)))

    # Coal, and the fire is in. Timed from here: the countdown only runs lit.
    game.clear_inventory()
    game.hold(COAL)
    clicks = game.click_until(lambda: game.block_is(pot, "%s[lit=true]" % POT))
    lit_at = time.monotonic()
    expect.that("furnace fuel lights the fire", clicks, _took(clicks))
    expect.equal("and is consumed", game.count_items(COAL), 0)

    done = game.wait_until(lambda: game.block_is(pot, "%s[pot=2]" % POT),
                           timeout=COOK_SECONDS + MARGIN + 5.0, interval=0.25)
    elapsed = time.monotonic() - lit_at
    expect.that("with fuel the pot finishes within cookTime plus a margin",
                done and elapsed <= COOK_SECONDS + MARGIN,
                "%.1fs for a cookTime of %.0fs" % (elapsed, COOK_SECONDS))

    # The bowl, and the meal one food predicts.
    game.clear_inventory()
    game.hold(BOWL)
    clicks = game.click_until(lambda: game.count_items(WEAK_SOUP) == 1)
    expect.that("one food ladles out a Weak Soup", clicks,
                "%s; %s" % (_meals_said(game), _took(clicks)))
    expect.equal("the bowl is consumed", game.count_items(BOWL), 0)
    emptied = _contents(game, pot)
    expect.that("and the pot runs dry", game.block_is(pot, "%s[pot=0]" % POT)
                and emptied["Base"] == "NONE", "pot %s" % _said(emptied))


# ------------------------------------------------------------------ pot two

def _soup(game, expect, pot):
    """One food and one flavouring: tier two."""
    _aim(game, pot)
    _start(game, expect, pot, "second pot")
    _feed(game, expect, pot, CARROT, food=1)
    _feed(game, expect, pot, SALT, flavour=1)
    _cooked(game, expect, pot, "one food and salt")

    game.clear_inventory()
    game.hold(BOWL)
    clicks = game.click_until(lambda: game.count_items(SOUP) == 1)
    expect.that("one food and salt ladle out a Soup", clicks,
                "%s; %s" % (_meals_said(game), _took(clicks)))


# ---------------------------------------------------------------- pot three

def _stew(game, expect, pot):
    """Two foods and one flavouring: tier three, and the refused item."""
    _aim(game, pot)
    _start(game, expect, pot, "third pot")
    _feed(game, expect, pot, CARROT, food=1)
    _feed(game, expect, pot, TOMATO, food=2)
    _feed(game, expect, pot, SALT, flavour=1)
    _cooked(game, expect, pot, "two foods and salt")

    # The null item, on a pot with everything in it: none of base, food,
    # flavouring or fuel, so the pot passes and nothing moves. The bowl a
    # moment later is the control that says the same aim reaches the pot.
    game.clear_inventory()
    game.hold(APPLE)
    before = _contents(game, pot)
    game.right_click()
    time.sleep(0.6)
    after = _contents(game, pot)
    same = all(after[key] == before[key] for key in ("Base", "Food", "Flavour", "CookProgress"))
    expect.that("an item the pot does not list is refused, with the pot unchanged",
                same and game.block_is(pot, "%s[pot=2,lit=true]" % POT),
                "pot %s" % _said(after))
    expect.that("and the inventory unchanged",
                game.count_items(APPLE) == 1 and _meals(game) == 0,
                "%d apples, %d meals" % (game.count_items(APPLE), _meals(game)))

    game.clear_inventory()
    game.hold(BOWL)
    clicks = game.click_until(lambda: game.count_items(STEW) == 1)
    expect.that("two foods and salt ladle out a Stew", clicks,
                "%s; %s" % (_meals_said(game), _took(clicks)))


# -------------------------------------------------------------------- steps

def _start(game, expect, pot, which):
    """Water, then coal, on a fresh pot; the fire is in before the food."""
    game.clear_inventory()
    game.hold(WATER)
    clicks = game.click_until(lambda: game.block_is(pot, "%s[pot=1]" % POT))
    expect.that("water starts the %s" % which, clicks, _took(clicks))
    game.clear_inventory()
    game.hold(COAL)
    clicks = game.click_until(lambda: game.block_is(pot, "%s[lit=true]" % POT))
    expect.that("and coal lights it", clicks, _took(clicks))


def _feed(game, expect, pot, item, food=None, flavour=None):
    key, count = ("Food", food) if food is not None else ("Flavour", flavour)
    game.clear_inventory()
    game.hold(item)
    clicks = game.click_until(lambda: _contents(game, pot)[key] == count)
    expect.that("%s goes in as %s number %d" % (item, key.lower(), count), clicks,
                _took(clicks))


def _cooked(game, expect, pot, what):
    started = time.monotonic()
    done = game.wait_until(lambda: game.block_is(pot, "%s[pot=2]" % POT),
                           timeout=COOK_SECONDS + MARGIN + 5.0, interval=0.25)
    expect.that("%s cook through" % what, done,
                "%.1fs since the last ingredient" % (time.monotonic() - started))


# ------------------------------------------------------------------ reading

def _aim(game, pot):
    """Two blocks west of the pot, looking at its west face over the spacer."""
    game.tp(pot[0] - 1.5, pot[1], pot[2] + 0.5, AIM_YAW, AIM_PITCH)


def _contents(game, pot):
    """The block entity's own record: base, counts and both timers."""
    raw = game.block_data(pot)
    found = {"Base": "", "Food": -1, "Flavour": -1, "CookProgress": -1, "BurnTime": -1}
    for key in found:
        match = re.search(r'\b%s: (?:"([A-Z_]+)"|(-?\d+))' % key, raw)
        if match:
            found[key] = match.group(1) if match.group(1) is not None else int(match.group(2))
    return found


def _said(contents):
    return "%s/%s food/%s flavour, cook %s, burn %s" % (
        contents["Base"], contents["Food"], contents["Flavour"],
        contents["CookProgress"], contents["BurnTime"])


def _meals(game):
    return sum(game.count_items(meal) for meal in MEALS)


def _meals_said(game):
    counts = ["%d %s" % (game.count_items(meal), meal) for meal in MEALS if game.count_items(meal)]
    return ", ".join(counts) or "no meal"


def _took(clicks):
    if not clicks:
        return "never landed"
    return "one click" if clicks == 1 else "%d clicks" % clicks
