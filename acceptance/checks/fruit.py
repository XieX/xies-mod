"""A leaf ripening, and the harvest that leaves it standing.

``fruit`` is what replaced 2013's ``sheds`` in October 2026. A fruit leaf
carries an ``age``: it ripens on its own random ticks when it is on the outside
of the canopy, a right-click on a ripe one pops the fruit out and resets it,
and bone meal ripens one, the way it puts the berries on a cave vine. A
``persistent`` leaf — one a player placed — bears nothing.

Six leaves, each on a log of its own so that it measures one from
``#minecraft:logs`` and never decays, and each built canopy first and trunk
last for the reason ``decay`` gives: a leaf placed by a command keeps the
distance it was placed with until a neighbour changes. One is placed ripe and
picked; one is bare and bone-mealed; one is bare and left to the random ticks;
and two are the nulls for those, taken by the same measurement over the same
window: the persistent one, and a bare one with a ripe leaf beside it, which
is the rule that spaces the fruit out and caps what an unpicked tree holds.

The ripening roll is a quarter of a crop's, so the ticks run at 2000 rather
than the 400 the other checks use: a leaf then ripens in ten seconds on
average, which puts the sixty-second timeout at three in a thousand, and the
thirty-second window the nulls get afterwards would have ripened an eligible
leaf nineteen times in twenty.

The pick is read in the player's hands rather than on the ground. The fruit
pops out of the face that was clicked — the top, with the player standing on
the leaf — and is picked up within a second, which is the race ``count_items``
exists to avoid.
"""

import time

from ..rig.arena import Y, lane

NEEDS_CLIENT = True

LEAVES = "xiesmod:apple_leaves"
LOG = "minecraft:oak_log"
FRUIT = "minecraft:apple"
APART = 4
TIMEOUT = 60.0
SPEED = 2000

#: How long the two nulls go on ticking after the bare leaf has ripened, so that
#: the leaves which are supposed to stay bare are given a fair chance to fail.
WINDOW = 30.0


def run(game, expect):
    x0, z0 = lane("fruit")
    game.enter(x0 + 1.5 * APART, z0 + 6)
    game.clear((x0 - 4, z0 - 4), (x0 + 4 * APART + 5, z0 + 6), floor="minecraft:grass_block")
    game.kill_items()
    game.gamemode("creative")
    game.clear_inventory()

    ripe = (x0, Y + 1, z0)
    bare = (x0 + APART, Y + 1, z0)
    wild = (x0 + 2 * APART, Y + 1, z0)
    hedge = (x0 + 3 * APART, Y + 1, z0)
    crowded = (x0 + 4 * APART, Y + 1, z0)
    beside = (x0 + 4 * APART + 1, Y + 1, z0)
    for pos, state in ((ripe, "age=1"), (bare, "age=0"), (wild, "age=0"),
                       (hedge, "age=0,persistent=true"), (crowded, "age=0"),
                       (beside, "age=1")):
        game.setblock(pos, "%s[%s]" % (LEAVES, state))
        game.setblock((pos[0], Y, pos[2]), LOG)

    standing = all(game.block_is(pos, LEAVES)
                   for pos in (ripe, bare, wild, hedge, crowded, beside))
    if not expect.that("six leaves stand on six logs", standing,
                       "nothing below can mean anything without them"):
        return

    # ------------------------------------------------------------ the harvest
    game.stand_on(ripe)
    picked = game.click_until(lambda: game.block_is(ripe, "%s[age=0]" % LEAVES))
    expect.that("a right-click on a ripe leaf leaves it standing and bare", picked,
                "it is still %s" % _state(game, ripe) if not picked else "")
    got = game.wait_until(lambda: game.count_items(FRUIT), 5.0)
    expect.that("and hands over its apple", got == 1, "%d apples in hand" % got)

    game.stand_on(bare)
    game.right_click()
    time.sleep(1.0)
    expect.that("a bare leaf gives nothing to the same click",
                game.block_is(bare, "%s[age=0]" % LEAVES) and game.count_items(FRUIT) == 1,
                "the leaf is %s and there are %d apples in hand"
                % (_state(game, bare), game.count_items(FRUIT)))

    # ------------------------------------------------------------- bone meal
    game.hold("minecraft:bone_meal", 16)
    game.stand_on(bare)
    clicks = game.click_until(lambda: game.block_is(bare, "%s[age=1]" % LEAVES))
    expect.that("bone meal ripens a bare leaf", clicks,
                "it is still %s" % _state(game, bare) if not clicks else "")

    game.stand_on(hedge)
    refused = not game.click_until(lambda: game.block_is(hedge, "%s[age=1]" % LEAVES))
    expect.that("and is refused by a persistent leaf, which a player placed", refused,
                "a hedge of sheared leaves is not an orchard")

    # --------------------------------------------------- ripening on its own
    game.tp(x0 + 2 * APART, Y, z0 + 5, 0.0, 0.0)
    game.random_ticks(SPEED)
    try:
        started = time.monotonic()
        ripened = game.wait_until(lambda: game.block_is(wild, "%s[age=1]" % LEAVES), TIMEOUT)
        # The bare leaf can ripen in a second, which would leave the nulls
        # barely tested; all of them keep ticking for a fixed window instead.
        time.sleep(WINDOW)
        still = game.block_is(hedge, "%s[age=0]" % LEAVES)
        spaced = game.block_is(crowded, "%s[age=0]" % LEAVES)
    finally:
        game.random_ticks(3)

    expect.that("a leaf open to the air ripens on its own random ticks", ripened,
                "after %.0fs at random_tick_speed %d" % (time.monotonic() - started - WINDOW, SPEED)
                if ripened else "still bare after %.0fs at random_tick_speed %d" % (TIMEOUT, SPEED))
    expect.that("the persistent leaf does not, over the same ticks", still,
                "it is %s" % _state(game, hedge) if not still else "")
    expect.that("and nor does a bare leaf with a ripe one beside it", spaced,
                "it is %s, beside a leaf that is %s" % (_state(game, crowded), _state(game, beside))
                if not spaced else "")


def _state(game, pos):
    """Which of the leaf's states is there, for a failure that would otherwise say nothing."""
    for state in ("age=0", "age=1"):
        if game.block_is(pos, "%s[%s]" % (LEAVES, state)):
            return state
    return "not a leaf"
